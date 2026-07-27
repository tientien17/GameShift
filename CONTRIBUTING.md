# Contributing to GameShift

Thank you for considering contributing! This is a small project so any help — issues, PRs, discussions — is appreciated.

## How to Contribute

### Reporting Bugs

Open an issue with:

- Device model and Android version
- Controller make/model and connection type (USB / Bluetooth)
- Shizuku version and startup method (root / wireless debugging / ADB)
- Step-by-step reproduction
- What you expected vs what happened
- Logcat output (if available): `adb logcat -s ControllerMonitor HomeRouterActivity LauncherSwitcher`

### Feature Requests

Open an issue describing:

- What you want to achieve
- Why it's useful for the project
- Any relevant prior art or references

### Pull Requests

1. Fork the repo and create a feature branch from `main`.
2. Follow the existing code style (see below).
3. Test your changes on a real device if possible.
4. Keep PRs focused — one change per PR.
5. Write a clear commit message and PR description.

## Development Setup

### Prerequisites

- JDK 17+
- Android Studio (recommended) or command-line build tools
- A device running Android 8+ with Shizuku installed

### Building

```bash
./gradlew assembleDebug
```

Install on device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Debugging

Run with verbose logging:

```bash
adb shell setprop log.tag.ControllerMonitor VERBOSE
adb shell setprop log.tag.HomeRouterActivity VERBOSE
adb shell setprop log.tag.LauncherSwitcher VERBOSE
```

View logs:

```bash
adb logcat -s ControllerMonitor HomeRouterActivity LauncherSwitcher GameShiftApp
```

## Code Style

- **Kotlin** — Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **No `as` / `@Suppress`** — Avoid type coercion and warning suppression unless absolutely necessary.
- **No `@ts-ignore` / `@ts-expect-error`** — Not applicable (Kotlin project), but the same principle: fix the type properly.
- **Formatting** — Use the project's `.editorconfig` settings. If using Android Studio, it should auto-format.
- **Naming** — Classes: PascalCase. Functions/variables: camelCase. Constants: UPPER_SNAKE_CASE.
- **Error handling** — Use sealed class results (`Result<T>`), not exceptions for control flow.
- **Comments** — Document public API and non-obvious logic. Don't comment obvious code.

## Architecture Notes

The project follows a simple layered structure:

```
detector/      — Controller detection logic (InputDevice, Bluetooth)
shizuku/       — Shizuku integration for launcher switching
service/       — Foreground service with monitoring loop
util/          — Shared preferences wrapper
*.kt (root)    — Activities, Application class, broadcast receivers
```

When adding features:
- Keep `LauncherSwitcher` as the single point of launcher-switching logic.
- Detection and switching should be decoupled — the service bridges them.
- `HomeRouterActivity` should remain stateless — it reads controller state and routes.

## Testing

Manual testing on a real device is the primary approach. When writing code:

1. Build and install the APK.
2. Start the service in the app.
3. Connect/disconnect a controller and verify the launcher switches.
4. Press HOME with and without a controller connected.
5. Check logcat for any errors or unexpected behavior.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
