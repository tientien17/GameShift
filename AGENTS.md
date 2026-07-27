# GameShift AGENTS.md

Auto-switch HOME launcher when a game controller connects.

## Project Overview

GameShift is an Android app that monitors game controller connections and automatically changes the system's default HOME launcher. When a controller is connected, pressing HOME opens the configured game launcher instead of the normal launcher.

**Key behavior:**
1. Controller connects → GameShift sets itself as default HOME launcher via Shizuku
2. User presses HOME → `HomeRouterActivity` routes to the game launcher or normal launcher based on controller state
3. Controller disconnects → Original default launcher is restored

## Architecture — Component Map

```
GameShiftApp (Application)
  ├── Prefs                     — SharedPreferences wrapper
  ├── MainActivity              — Settings UI (Compose): launcher selection, service control
  ├── OnboardingActivity        — 7-page walkthrough (Compose): Shizuku setup, config
  │
  ├── HomeRouterActivity        — Transparent activity: the actual HOME handler
  │   (Registered as category HOME launcher)
  │
  ├── ControllerMonitorService  — Foreground service: detects connect/disconnect
  │   ├── InputDeviceListener   — Real-time input device callbacks
  │   ├── BluetoothReceiver     — Backup for BT controllers
  │   └── Periodic polling      — Fallback every 10s
  │
  ├── ControllerDetector        — Detection logic (singleton object)
  ├── LauncherSwitcher          — Shizuku-powered launcher switching
  │
  ├── BootReceiver              — Restart service after reboot
  └── UsbReceiverActivity       — Trampoline for USB_DEVICE_ATTACHED (Android 12+)
```

## Key Files

| File | Role |
|---|---|
| `GameShiftApp.kt` | Application class, notification channel, global singleton |
| `MainActivity.kt` | Compose settings UI: status cards, launcher pickers, start/stop service |
| `HomeRouterActivity.kt` | Intercepts HOME press, routes to game or normal launcher based on controller state |
| `service/ControllerMonitorService.kt` | Foreground service, listens for controller events, triggers launcher switch |
| `detector/ControllerDetector.kt` | `isGameController()`, `isAnyControllerConnected()`, `isAnyControllerLikelyConnected()` |
| `shizuku/LauncherSwitcher.kt` | Shizuku shell commands to set/remove HOME role holder |
| `util/Prefs.kt` | `gameLauncherPackage`, `normalLauncherPackage`, `originalLauncherPackage`, etc. |
| `onboarding/OnboardingActivity.kt` | 7-page walkthrough: welcome → how it works → Shizuku install → activate → permission → configure launchers → start service |
| `BootReceiver.kt` | `ACTION_BOOT_COMPLETED` → restarts monitoring service |
| `UsbReceiverActivity.kt` | Trampoline activity for USB attach events (Android 12+ limitation) |

## Build Environment

- **JDK**: 17 (Zulu 17.0.14)
- **Android SDK**: 35 (compileSdk), 35 (targetSdk), 26 (minSdk)
- **Build tools**: 35.0.0
- **Build system**: Gradle 8.x with Kotlin DSL
- **Key deps**: Shizuku v13.1.5, AndroidX Compose BOM, Material3
- **Build command**: `./gradlew assembleDebug`
- **Output**: `app/build/outputs/apk/debug/app-debug.apk`
- **ProGuard**: `proguard-rules.pro` with Shizuku keep rules

## Wireless ADB Debugging Setup (for deployment to physical device)

### Prerequisites

1. Developer Options enabled on phone (Settings > About Phone > Tap Build Number 7 times)
2. Wireless Debugging enabled (Settings > Developer Options > Wireless Debugging)
3. Phone and development PC on the same WiFi network

### ADK Path

ADB is at `C:\Users\ADMIN\AppData\Local\Android\Sdk\platform-tools\adb.exe`

### Pairing (first time only)

1. On phone: Developer Options > Wireless Debugging > Pair device with pairing code
2. Note the IP, port, and 6-digit pairing code shown
3. On PC:
   ```powershell
   cd C:\Users\ADMIN\AppData\Local\Android\Sdk\platform-tools
   .\adb pair <ip>:<pairing_port>   # e.g. 192.168.x.x:12345
   # Enter the 6-digit pairing code when prompted
   ```

### Connecting

```powershell
.\adb connect <ip>:<connect_port>   # e.g. 192.168.x.x:54321
# Verify with:
.\adb devices
```

### Deploy APK

```powershell
.\adb connect <ip>:<connect_port>
.\adb uninstall com.gameshift.app   # Clean install
.\adb install -r -t .\app\build\outputs\apk\debug\app-debug.apk
```

### Logcat while debugging

```powershell
.\adb shell pidof com.gameshift.app   # Get PID
.\adb logcat -v time | Select-String -Pattern "ControllerMonitor|HomeRouterActivity|LauncherSwitcher|GameShiftApp"
```

### Multiple devices

If multiple devices are connected, use `-s <serial>`:
```powershell
.\adb devices
.\adb -s <serial> install -r -t app\build\outputs\apk\debug\app-debug.apk
```

## Bug Fix History

### Fix 1: HOME button jumps to normal launcher (main fix)

**Root cause**: `HomeRouterActivity.getLaunchIntentForPackage()` returns null for HOME-category apps that don't have a MAIN/LAUNCHER intent filter (some game launchers only register CATEGORY_HOME).

**Fix**: Use `packageManager.queryIntentActivities()` with CATEGORY_HOME to find the exact component, then `setClassName()` to launch directly. Fallback to `getLaunchIntentForPackage()` if not found.

### Fix 2: Launcher switching fails silently

**Root cause**: `LauncherSwitcher` attempted `set-home-activity` directly without properly setting the role holder first. On Android 11+, proper HOME role management requires:
1. `cmd role set-role-holder android.app.role.HOME <pkg>` (preferred)
2. `cmd role remove-role-holder` then `cmd role add-role-holder` (fallback)
3. `cmd package set-home-activity` (last resort fallback)

Also added `removeHomeLauncher()` before `setHomeLauncher()` in `restoreOriginalLauncher()` to avoid conflicts.

### Fix 3: Service restores launcher on destroy when controller still connected

**Root cause**: `ControllerMonitorService.onDestroy()` was unconditionally restoring the normal launcher. If the service was killed (e.g., crash or battery optimization) while a controller was still connected, the user would lose game mode.

**Fix**: Check `ControllerDetector.isAnyControllerConnected()` before restoring in `onDestroy()`.

### Fix 4: Shizuku SecurityException on newProcess

**Root cause**: `Shizuku.newProcess()` requires `checkSelfPermission()` to return `PERMISSION_GRANTED`. The original code called `newProcess` via reflection but only checked `Shizuku.pingBinder()` — not the permission.

**Fix**: Added `Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED` check in both `runShell()` and `isShizukuReady()` before attempting `newProcess`.

### Fix 5: Rotation control logic doesn't restore on controller disconnect

**Root cause**: Screen rotation settings were being modified in `HomeRouterActivity` but were not saved or restored cleanly when a controller disconnected, leaving the screen locked in landscape mode.

**Fix**: Updated `LauncherSwitcher.kt` to save the device's original rotation state (`accelerometer_rotation` and `user_rotation`) before applying game-mode rotation. Added `restoreAutoRotate()` which restores these values, returning the device to its pre-connected state (e.g. re-enabling auto-rotation or returning to the user's specific locked rotation).

### Fix 6: Foreground service killed by background optimization

**Root cause**: On some devices (particularly Chinese emulation consoles and handhelds), aggressive power managers aggressively kill background services, disabling the controller connection monitoring.

**Fix**: Added the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission and implemented a "Battery Optimization" status card on the settings home screen. If optimized, it alerts the user and provides a button to open system settings to exclude GameShift from battery optimization.

## Screen Rotation Modes

The app supports three distinct rotation configurations under "Game Mode Rotation":
1. **Retain Rotation**: Leaves the system rotation settings untouched.
2. **90° Landscape**: Forces the screen to standard landscape (user rotation 1) and disables accelerometer auto-rotation.
3. **-90° Reverse Landscape**: Forces the screen to reverse landscape (user rotation 3) and disables accelerometer auto-rotation.

These configurations are saved to `game_launcher_rotation_mode` in settings.

## Branding & Design

### Retro Arcade Theme
- **Aesthetic**: Retro arcade gaming — dark background, neon magenta/cyan/yellow on deep navy
- **Mascot**: A cute game controller character with big eyes and a crown, in neon colors
- **Fonts**: Space Mono (regular + bold) and Press Start 2P (regular) bundled in `res/font/`. Typography defined in `GameShiftTheme.kt` with PressStart2P for `displayLarge` and SpaceMono for headings/body.

### Color Palette
| Role | Color | Hex |
|---|---|---|
| Background | Very dark blue-black | `#0A0A1A` |
| Surface | Dark purple | `#150A25` |
| Primary | Neon magenta | `#FF00FF` |
| Secondary | Cyan | `#00FFFF` |
| Accent/Tertiary | Yellow | `#FFFF00` |
| On-Background | Light gray | `#E0E0E0` |
| On-Surface | White | `#FFFFFF` |
| Error | Red | `#FF3333` |

### Resource Files
| File | Purpose |
|---|---|
| `res/drawable/ic_mascot_chatgpt.png` | Cute controller character mascot (PNG) |
| `res/drawable/ic_launcher_foreground.xml` | Adaptive icon foreground (108dp) |
| `res/drawable/ic_launcher_background.xml` | Adaptive icon background with CRT grid pattern |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive launcher icon definition |
| `res/mipmap-anydpi-v26/ic_launcher_round.xml` | Adaptive round launcher icon definition |
| `res/mipmap-hdpi/ic_launcher.png` | Fallback icon (pre-API 26) |
| `res/mipmap-hdpi/ic_launcher_foreground.png` | Icon foreground PNG (all densities) |
| `res/values/colors.xml` | Named color resources for retro arcade palette |
| `res/values/themes.xml` | Theme definitions using new palette |
| `res/drawable/ic_notification.xml` | Small gamepad icon for notification (unchanged) |
| `res/xml/device_filter.xml` | USB device filter for attach events |

### ComfyUI Local Setup
- **Location**: `C:\Users\ADMIN\Desktop\LOUISE\Repositories\ComfyUI_windows_portable`
- **GPU**: NVIDIA GeForce RTX 4060 Laptop GPU (8GB VRAM)
- **Status**: Running on `localhost:8188`
- **Model**: SDXL base 1.0 downloading (~6.8GB, via BITS)
- **Start**: `run_nvidia_gpu.bat` or `python_embeded\python.exe -s ComfyUI\main.py`
- **Use**: POST workflow JSON to `http://localhost:8188/prompt`

### Compose UI Updates
- `MainActivity.kt` — MaterialTheme colorScheme updated to retro arcade palette; header logo uses `R.mipmap.ic_launcher_foreground`
- `OnboardingActivity.kt` — Color scheme updated; welcome page shows `ic_launcher_foreground` (mipmap); completion page shows `ic_mascot`
- Icon references in `AndroidManifest.xml` changed from `@drawable/ic_notification` to `@mipmap/ic_launcher`

### Theme Extraction
- `GameShiftTheme.kt` — Shared Material3 theme composable extracted to `com.gameshift.app.theme`
- `AppUtils.kt` — Shared utility functions in `com.gameshift.app.util`

## Onboarding Flow Fixes

### Bug 1: Onboarding loop after closing app
**Root cause**: `onboardingCompleted` was only saved on the second button click ("Get Started"), not on "Start Service". Closing the app after starting service but before clicking the next button caused a re-onboarding loop.
**Fix**: `onboardingCompleted = true` is now saved when "Start Service" is clicked.

### Bug 2: "Get Started" label was ambiguous
**Fix**: Changed to "Open Settings" in both content area and bottom navigation bar. Bottom bar button also auto-starts the service.

## Current State & Known Issues

- App builds successfully (`./gradlew assembleDebug` passes)
- Launcher switching works via Shizuku shell commands (3-tier fallback strategy)
- `HomeRouterActivity` correctly routes HOME to configured game/normal launcher
- Controller detection uses InputDeviceListener + Bluetooth broadcast + 10s polling
- **Shizuku permission must be granted** — the app shows a setup card if Shizuku isn't ready
- `HomeRouterActivity` must be registered as a HOME category activity in AndroidManifest.xml for the routing to work
- `isAnyControllerLikelyConnected()` adds heuristic detection for generic USB HID gamepads that may not advertise `SOURCE_GAMEPAD`
- VectorDrawable branding assets work on API 21+ (with AppCompat); adaptive icons on API 26+
- SDXL base model downloading in background — once complete, ComfyUI can generate retro arcade promo/splash art
