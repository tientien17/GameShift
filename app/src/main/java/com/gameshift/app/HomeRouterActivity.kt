package com.gameshift.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.util.Log
import com.gameshift.app.BuildConfig
import com.gameshift.app.detector.ControllerDetector
import com.gameshift.app.shizuku.LauncherSwitcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeRouterActivity : Activity() {

    companion object {
        private const val TAG = "HomeRouterActivity"
        private const val HOME_ACTION = Intent.ACTION_MAIN
        private const val HOME_CATEGORY = Intent.CATEGORY_HOME
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeToApp()
        finish()
    }

    private fun routeToApp() {
        val prefs = GameShiftApp.instance.prefs
        val isControllerConnected = ControllerDetector.isAnyControllerConnected()

        if (BuildConfig.DEBUG) Log.d(TAG, "HOME intercepted. Controller connected: $isControllerConnected" +
                "  devices=${ControllerDetector.getConnectedControllerNames()}")

        val targetPackage = if (isControllerConnected) {
            prefs.gameLauncherPackage
        } else {
            prefs.normalLauncherPackage
        }

        if (targetPackage.isNullOrBlank()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "No target launcher configured, showing picker")
            startSystemHomePicker()
            return
        }

        launchHomeApp(targetPackage)
    }

    private fun launchHomeApp(packageName: String) {
        try {
            val prefs = GameShiftApp.instance.prefs
            val isGameLauncher = packageName == prefs.gameLauncherPackage
            if (isGameLauncher) {
                try {
                    val switcher = LauncherSwitcher(this)
                    if (switcher.isShizukuReady()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            switcher.applyRotationMode(prefs.gameLauncherRotationMode)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to force landscape", e)
                }
            }

            val homes = getHomeActivities()
            val target = homes.firstOrNull { it.activityInfo.packageName == packageName }
            if (target != null) {
                val intent = Intent(HOME_ACTION).apply {
                    addCategory(HOME_CATEGORY)
                    setClassName(target.activityInfo.packageName, target.activityInfo.name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                startActivity(intent)
                if (BuildConfig.DEBUG) Log.d(TAG, "Launched HOME app by component: $packageName/${target.activityInfo.name}")
            } else {
                if (BuildConfig.DEBUG) Log.w(TAG, "Package $packageName not found in HOME activities, trying launch intent")
                val fallback = packageManager.getLaunchIntentForPackage(packageName)
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(fallback)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Launched via launch intent: $packageName")
                } else {
                    if (BuildConfig.DEBUG) Log.w(TAG, "No launch intent for: $packageName")
                    startSystemHomePicker()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to launch $packageName", e)
            startSystemHomePicker()
        }
    }

    private fun startSystemHomePicker() {
        try {
            val intent = Intent(HOME_ACTION).apply {
                addCategory(HOME_CATEGORY)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(Intent.createChooser(intent, "Select Launcher"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show launcher picker", e)
        }
    }

    private fun getHomeActivities(): List<ResolveInfo> {
        val intent = Intent(HOME_ACTION).apply { addCategory(HOME_CATEGORY) }
        return packageManager.queryIntentActivities(intent, 0)
    }
}
