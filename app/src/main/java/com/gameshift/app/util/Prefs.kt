package com.gameshift.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * SharedPreferences wrapper for GameShift settings.
 */
class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gameshift", Context.MODE_PRIVATE)

    /** Package name of the app to launch when HOME is pressed with a controller connected. */
    var gameLauncherPackage: String?
        get() = prefs.getString(KEY_GAME_LAUNCHER, null)
        set(value) = prefs.edit { putString(KEY_GAME_LAUNCHER, value) }

    /** Package name of the normal launcher (restored when no controller). */
    var normalLauncherPackage: String?
        get() = prefs.getString(KEY_NORMAL_LAUNCHER, null)
        set(value) = prefs.edit { putString(KEY_NORMAL_LAUNCHER, value) }

    /** The launcher that was default before GameShift took over — used for restore. */
    var originalLauncherPackage: String?
        get() = prefs.getString(KEY_ORIGINAL_LAUNCHER, null)
        set(value) = prefs.edit { putString(KEY_ORIGINAL_LAUNCHER, value) }

    /** Whether a controller was connected on last known state. */
    var wasControllerConnected: Boolean
        get() = prefs.getBoolean(KEY_WAS_CONTROLLER_CONNECTED, false)
        set(value) = prefs.edit { putBoolean(KEY_WAS_CONTROLLER_CONNECTED, value) }

    /** Whether the monitoring service should auto-start on boot. */
    var autoStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_START, value) }

    /** Whether Shizuku was authorized by the user. */
    var shizukuAuthorized: Boolean
        get() = prefs.getBoolean(KEY_SHIZUKU_AUTHORIZED, false)
        set(value) = prefs.edit { putBoolean(KEY_SHIZUKU_AUTHORIZED, value) }

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, value) }

    var gameLauncherRotationMode: Int
        get() = prefs.getInt(KEY_ROTATION_MODE, 1)
        set(value) = prefs.edit { putInt(KEY_ROTATION_MODE, value) }

    var originalAccelerometerRotation: Int
        get() = prefs.getInt(KEY_ORIG_ACCEL, 1)
        set(value) = prefs.edit { putInt(KEY_ORIG_ACCEL, value) }

    var originalUserRotation: Int
        get() = prefs.getInt(KEY_ORIG_USER_ROT, 0)
        set(value) = prefs.edit { putInt(KEY_ORIG_USER_ROT, value) }

    var hasSavedRotation: Boolean
        get() = prefs.getBoolean(KEY_HAS_SAVED_ROT, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_SAVED_ROT, value) }

    /** Clear saved launchers (for reset). */
    fun clearLaunchers() {
        prefs.edit {
            remove(KEY_GAME_LAUNCHER)
            remove(KEY_NORMAL_LAUNCHER)
            remove(KEY_ORIGINAL_LAUNCHER)
            remove(KEY_WAS_CONTROLLER_CONNECTED)
            remove(KEY_ROTATION_MODE)
            remove(KEY_ORIG_ACCEL)
            remove(KEY_ORIG_USER_ROT)
            remove(KEY_HAS_SAVED_ROT)
        }
    }

    companion object {
        private const val KEY_GAME_LAUNCHER = "game_launcher_package"
        private const val KEY_NORMAL_LAUNCHER = "normal_launcher_package"
        private const val KEY_ORIGINAL_LAUNCHER = "original_launcher_package"
        private const val KEY_WAS_CONTROLLER_CONNECTED = "was_controller_connected"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_SHIZUKU_AUTHORIZED = "shizuku_authorized"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ROTATION_MODE = "game_launcher_rotation_mode"
        private const val KEY_ORIG_ACCEL = "original_accelerometer_rotation"
        private const val KEY_ORIG_USER_ROT = "original_user_rotation"
        private const val KEY_HAS_SAVED_ROT = "has_saved_rotation"
    }
}
