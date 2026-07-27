package com.gameshift.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.gameshift.app.BuildConfig
import com.gameshift.app.shizuku.IUserShellService
import rikka.shizuku.Shizuku

/**
 * Manages switching the default HOME launcher via Shizuku shell commands.
 *
 * Strategy (tried in order):
 * 1. `cmd role set-role-holder android.app.role.HOME <pkg>` — replaces current holder (Android 11+)
 * 2. `cmd role remove-role-holder <old> && cmd role add-role-holder <new>` — manual swap
 * 3. `cmd package set-home-activity <component>` — deprecated but widely supported fallback
 */
class LauncherSwitcher(private val context: Context) {

    private var shellService: IUserShellService? = null
    private val shellConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shellService = IUserShellService.Stub.asInterface(binder)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
        }
    }

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context, ShizukuShellService::class.java)
        ).apply {
            processNameSuffix("shell")
            debuggable(BuildConfig.DEBUG)
            version(1)
        }
    }

    /**
     * Get the current default launcher package name (from app's own process).
     */
    fun getCurrentDefaultLauncher(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName
    }

    /**
     * Validate that a package name is safe to use in shell commands.
     * Android package names can only contain [a-zA-Z0-9._]
     */
    private fun isValidPackageName(packageName: String): Boolean {
        return packageName.matches(Regex("^[a-zA-Z0-9._]+$"))
    }

    /**
     * Validate a package name is installed (safety check before shell execution).
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    fun setHomeLauncher(packageName: String): Result<String> {
        if (!isValidPackageName(packageName) || !isPackageInstalled(packageName)) {
            return Result.failure(IllegalArgumentException("Invalid or unknown package: $packageName"))
        }
        val setResult = runShell("cmd role set-role-holder android.app.role.HOME $packageName")
        if (setResult.isSuccess) return setResult

        val current = getCurrentDefaultLauncher()
        if (current != null && current != packageName) {
            runShell("cmd role remove-role-holder android.app.role.HOME $current")
        }
        val addResult = runShell("cmd role add-role-holder android.app.role.HOME $packageName")
        if (addResult.isSuccess) return addResult

        val component = "${context.packageName}/.HomeRouterActivity"
        runShell("cmd package set-home-activity $component")
            .onSuccess { return Result.success("fallback") }

        return addResult
    }

    /**
     * Remove the HOME role from [packageName].
     */
    fun removeHomeLauncher(packageName: String): Result<String> {
        if (!isValidPackageName(packageName)) {
            return Result.failure(IllegalArgumentException("Invalid package name: $packageName"))
        }
        return runShell("cmd role remove-role-holder android.app.role.HOME $packageName")
    }

    /**
     * Force the device into landscape orientation via Shizuku.
     * Disables auto-rotate and sets rotation to landscape.
     */
    fun forceLandscape(): Result<String> {
        saveOriginalRotation()
        return runShell("settings put global accelerometer_rotation 0 && settings put system user_rotation 1")
    }

    /**
     * Apply specified rotation mode.
     * Mode: 0 = Retain, 1 = Landscape (90 deg), 3 = Reverse Landscape (-90 deg)
     */
    fun applyRotationMode(mode: Int): Result<String> {
        if (mode == 1 || mode == 3) {
            saveOriginalRotation()
            return if (mode == 1) {
                runShell("settings put global accelerometer_rotation 0 && settings put system user_rotation 1")
            } else {
                runShell("settings put global accelerometer_rotation 0 && settings put system user_rotation 3")
            }
        }
        return Result.success("retain")
    }

    /**
     * Save the original system rotation settings before modifying.
     */
    private fun saveOriginalRotation() {
        val prefs = com.gameshift.app.GameShiftApp.instance.prefs
        if (!prefs.hasSavedRotation) {
            try {
                val accelResult = runShell("settings get global accelerometer_rotation")
                val userRotResult = runShell("settings get system user_rotation")
                
                val accel = accelResult.getOrNull()?.trim()?.toIntOrNull() ?: 1
                val userRot = userRotResult.getOrNull()?.trim()?.toIntOrNull() ?: 0
                
                prefs.originalAccelerometerRotation = accel
                prefs.originalUserRotation = userRot
                prefs.hasSavedRotation = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save original rotation", e)
            }
        }
    }

    /**
     * Restore auto-rotate (re-enable accelerometer-based rotation).
     */
    fun restoreAutoRotate(): Result<String> {
        val prefs = com.gameshift.app.GameShiftApp.instance.prefs
        if (prefs.hasSavedRotation) {
            val accel = prefs.originalAccelerometerRotation
            val userRot = prefs.originalUserRotation
            prefs.hasSavedRotation = false
            return runShell("settings put global accelerometer_rotation $accel && settings put system user_rotation $userRot")
        }
        return runShell("settings put global accelerometer_rotation 1")
    }

    /**
     * Set this app (GameShift) as the default HOME launcher.
     * Saves the original launcher first so it can be restored later.
     *
     * @return true if switch was successful
     */
    fun switchToGameShift(): Boolean {
        val prefs = com.gameshift.app.GameShiftApp.instance.prefs
        if (prefs.originalLauncherPackage == null) {
            val current = getCurrentDefaultLauncher()
            if (current != null && current != context.packageName) {
                prefs.originalLauncherPackage = current
                if (BuildConfig.DEBUG) Log.d(TAG, "Saved original launcher: $current")
            }
        }
        val result = setHomeLauncher(context.packageName)
        val success = result.isSuccess
        if (BuildConfig.DEBUG) Log.d(TAG, if (success) "Switched to GameShift" else "Failed: ${result.exceptionOrNull()}")
        return success
    }

    /**
     * Restore the original HOME launcher that was active before GameShift took over.
     */
    fun restoreOriginalLauncher(): Boolean {
        val prefs = com.gameshift.app.GameShiftApp.instance.prefs
        val original = prefs.originalLauncherPackage ?: return false
        removeHomeLauncher(context.packageName)
        val result = setHomeLauncher(original)
        val success = result.isSuccess
        if (success) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Restored original launcher: $original")
        } else {
            Log.w(TAG, "Failed to restore launcher: ${result.exceptionOrNull()}")
        }
        return success
    }

    private fun runShell(command: String): Result<String> {
        return try {
            if (!Shizuku.pingBinder()) {
                return Result.failure(IllegalStateException("Shizuku not running"))
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return Result.failure(SecurityException("Shizuku permission not granted"))
            }

            if (shellService == null) {
                Shizuku.bindUserService(serviceArgs, shellConnection)
                // bindUserService is async — spin until connected or timeout
                var retries = 0
                while (shellService == null && retries < 20) {
                    Thread.sleep(50)
                    retries++
                }
            }

            val service = shellService ?: return Result.failure(
                IllegalStateException("Failed to connect to Shizuku shell service")
            )

            val result = service.exec(command)
            if (result.startsWith("ERROR[")) {
                Result.failure(RuntimeException(result))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Shell command failed", e)
            Result.failure(e)
        }
    }

    /**
     * Check if Shizuku is available and we have permission.
     */
    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Permission check failed", e)
            false
        }
    }

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku ready check failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "LauncherSwitcher"
    }
}
