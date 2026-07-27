package com.gameshift.app.service

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.IBinder
import android.util.Log
import android.view.InputDevice
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.withContext
import com.gameshift.app.BuildConfig
import com.gameshift.app.GameShiftApp
import com.gameshift.app.R
import com.gameshift.app.detector.ControllerDetector
import com.gameshift.app.shizuku.LauncherSwitcher
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO

/**
 * Foreground service that monitors for game controller connections and
 * disconnections, and automatically switches the HOME launcher accordingly.
 *
 * Detection methods (in priority order):
 * 1. **InputDeviceListener** — real-time callback when input devices are added/removed
 * 2. **Periodic polling** — fallback every 10s to catch devices the listener misses
 * 3. **Bluetooth ACL broadcasts** — backup for Bluetooth controllers
 *
 * On controller CONNECT:
 *   → Switch default launcher to GameShift via Shizuku
 *   → GameShift then routes HOME presses to the game launcher app
 *
 * On controller DISCONNECT:
 *   → Restore original default launcher via Shizuku
 */
class ControllerMonitorService : Service() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var launcherSwitcher: LauncherSwitcher
    private var pollingJob: Job? = null
    private var lastControllerState = false

    private val inputManager by lazy {
        getSystemService(INPUT_SERVICE) as InputManager
    }

    /** Real-time callback for input device changes. */
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            if (ControllerDetector.isGameController(deviceId)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "InputDevice ADDED (gamepad): $deviceId")
                onControllerStateChanged()
            }
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            if (ControllerDetector.isGameController(deviceId)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "InputDevice REMOVED (gamepad): $deviceId")
                onControllerStateChanged()
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            // Not typically needed, but check state just in case
        }
    }

    /** Backup: Bluetooth device connection state. */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(
                BluetoothDevice.EXTRA_DEVICE
            )
            val action = intent.action
            if (BuildConfig.DEBUG) Log.d(TAG, "Bluetooth event: $action device=${device?.name}")
            // InputDeviceListener should catch BT gamepads too, but this is backup
            onControllerStateChanged()
        }
    }

    override fun onCreate() {
        super.onCreate()
        launcherSwitcher = LauncherSwitcher(this)

        // Register input device listener
        inputManager.registerInputDeviceListener(inputDeviceListener, null)

        // Register Bluetooth receiver
        val btFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, btFilter, Context.RECEIVER_NOT_EXPORTED)

        // Initial state check
        lastControllerState = ControllerDetector.isAnyControllerConnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service created. Initial controller state: $lastControllerState")

        // Start periodic polling as fallback
        startPolling()

        // Sync launcher to initial state
        if (lastControllerState) {
            coroutineScope.launch {
                switchToGameMode()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            GameShiftApp.NOTIFICATION_ID,
            buildNotification(ControllerDetector.isAnyControllerConnected())
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        coroutineScope.cancel()
        try {
            inputManager.unregisterInputDeviceListener(inputDeviceListener)
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering listeners during destroy", e)
        }
        if (!ControllerDetector.isAnyControllerConnected()) {
            CoroutineScope(Dispatchers.IO).launch {
                restoreNormalMode()
            }
        }
        super.onDestroy()
    }

    /**
     * Called whenever the controller connection state may have changed.
     * Debounced — only fires when state actually toggles.
     */
    private fun onControllerStateChanged() {
        val isConnected = ControllerDetector.isAnyControllerConnected()
        if (isConnected != lastControllerState) {
            lastControllerState = isConnected
            if (BuildConfig.DEBUG) Log.d(TAG, "Controller state changed: connected=$isConnected")

            // Update notification
            val notification = buildNotification(isConnected)
            startForeground(GameShiftApp.NOTIFICATION_ID, notification)

            // Switch launcher on IO thread
            coroutineScope.launch {
                if (isConnected) {
                    switchToGameMode()
                } else {
                    restoreNormalMode()
                }
            }
        }
    }

    /**
     * Switch to game mode: set GameShift as the default HOME launcher.
     * Run on IO thread — Shizuku shell command is blocking.
     */
    private suspend fun switchToGameMode() = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Switching to game mode...")
        if (launcherSwitcher.isShizukuReady()) {
            val success = launcherSwitcher.switchToGameShift()
            if (success) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ControllerMonitorService, "Game On!", Toast.LENGTH_SHORT).show()
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Game mode ON")
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Failed to switch to game mode")
            }
        } else {
            Log.w(TAG, "Shizuku not ready — cannot auto-switch launcher")
        }
    }

    /**
     * Restore normal mode: set the original launcher back as default.
     */
    private suspend fun restoreNormalMode() = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Restoring normal mode...")
        if (launcherSwitcher.isShizukuReady()) {
            val success = launcherSwitcher.restoreOriginalLauncher()
            launcherSwitcher.restoreAutoRotate()
            if (BuildConfig.DEBUG) Log.d(TAG, if (success) "Normal mode restored" else "Failed to restore normal mode")
        } else {
            Log.w(TAG, "Shizuku not ready — cannot restore launcher")
        }
    }

    /**
     * Periodically check controller state as a fallback.
     * Some devices don't trigger InputDeviceListener reliably.
     */
    private fun startPolling() {
        pollingJob = coroutineScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                onControllerStateChanged()
            }
        }
    }

    private fun buildNotification(controllerConnected: Boolean) =
        NotificationCompat.Builder(this, GameShiftApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (controllerConnected) getString(R.string.status_controller_connected)
                else getString(R.string.status_controller_disconnected)
            )
            .setContentText(getString(R.string.service_running))
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        private const val TAG = "ControllerMonitor"
        private const val POLL_INTERVAL_MS = 10_000L // 10 seconds

        fun start(context: Context) {
            val intent = Intent(context, ControllerMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ControllerMonitorService::class.java))
        }
    }
}
