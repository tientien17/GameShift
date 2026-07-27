package com.gameshift.app

import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import android.os.PowerManager
import android.net.Uri
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gameshift.app.detector.ControllerDetector
import com.gameshift.app.onboarding.OnboardingActivity
import com.gameshift.app.service.ControllerMonitorService
import com.gameshift.app.shizuku.LauncherSwitcher
import com.gameshift.app.theme.GameShiftTheme
import com.gameshift.app.util.Prefs
import com.gameshift.app.util.getAppName
import com.gameshift.app.util.getHomeLauncherPackages
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

/**
 * Main settings activity for GameShift.
 *
 * Allows the user to:
 * - Select which app to launch as the "game launcher" (when controller connected)
 * - Select which app to use as the "normal launcher" (when no controller)
 * - Start/stop the controller monitoring service
 * - Check current status (controller connected, Shizuku state, service state)
 */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var launcherSwitcher: LauncherSwitcher
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = GameShiftApp.instance.prefs
        launcherSwitcher = LauncherSwitcher(this)

        // First launch? Show onboarding before settings
        if (!prefs.onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContent {
            GameShiftTheme {
                GameShiftContent()
            }
        }
    }

    @Composable
    private fun GameShiftContent() {
        var refreshTrigger by remember { mutableIntStateOf(0) }
        var gameLauncher by remember { mutableStateOf(prefs.gameLauncherPackage) }
        var normalLauncher by remember { mutableStateOf(prefs.normalLauncherPackage) }
        var isStarting by remember { mutableStateOf(false) }

        // Force recomposition on refresh
        val status = remember(gameLauncher, normalLauncher, refreshTrigger) {
            AppStatus(
                controllerConnected = ControllerDetector.isAnyControllerConnected(),
                controllers = ControllerDetector.getConnectedControllerNames(),
                shizukuReady = launcherSwitcher.isShizukuReady(),
                serviceRunning = isServiceRunning(),
                gameLauncher = gameLauncher,
                normalLauncher = normalLauncher
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.onb_welcome_subtitle),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "GameShift Logo",
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Status Cards ──
                StatusCard(status)
 
                Spacer(Modifier.height(16.dp))

                BatteryOptimizationCard()

                Spacer(Modifier.height(16.dp))

                 // ── Launcher Selection ──
                 LauncherSelector(
                     title = stringResource(R.string.title_select_game_launcher),
                     description = stringResource(R.string.desc_select_game_launcher),
                     currentPackage = status.gameLauncher,
                     onSelect = { showLauncherPickerDialog(GAME_LAUNCHER_REQUEST) { pkg -> gameLauncher = pkg } }
                 )
 
                 Spacer(Modifier.height(8.dp))

                 RotationModeSelector(
                     currentMode = prefs.gameLauncherRotationMode,
                     onModeSelected = { mode ->
                         prefs.gameLauncherRotationMode = mode
                         refreshTrigger++
                     }
                 )

                 Spacer(Modifier.height(12.dp))

                LauncherSelector(
                    title = stringResource(R.string.title_select_normal_launcher),
                    description = stringResource(R.string.desc_select_normal_launcher),
                    currentPackage = status.normalLauncher,
                    onSelect = { showLauncherPickerDialog(NORMAL_LAUNCHER_REQUEST) { pkg -> normalLauncher = pkg } }
                )

                Spacer(Modifier.height(24.dp))

                // ── Action Buttons ──
                Button(
                    onClick = {
                        isStarting = true
                        ControllerMonitorService.start(this@MainActivity)
                        scope.launch {
                            delay(500)
                            isStarting = false
                            refreshTrigger++
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isStarting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.btn_start), fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        ControllerMonitorService.stop(this@MainActivity)
                        refreshTrigger++
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_stop), fontSize = 16.sp)
                }

                Spacer(Modifier.height(8.dp))

                // ── Shizuku Setup ──
                if (!status.shizukuReady) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.shizuku_not_running),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Auto-switching needs Shizuku. Install and start Shizuku from the Shizuku app, then return here.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                try {
                                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", "moe.shizuku.manager", null)
                                    })
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to open Shizuku Manager settings", e)
                                    Toast.makeText(this@MainActivity, "Please install Shizuku from GitHub", Toast.LENGTH_LONG).show()
                                }
                            }) {
                                Text("Open Shizuku Manager")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Quick Info ──
                Text(
                    text = "How it works:\n" +
                            "1. Connect a game controller → GameShift sets itself as the default HOME\n" +
                            "2. Press HOME on your controller → your game launcher opens\n" +
                            "3. Disconnect the controller → your normal launcher is restored",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // ── Auto-refresh status every 2 seconds ──
        DisposableEffect(Unit) {
            val job = scope.launch {
                while (isActive) {
                    delay(2000)
                    refreshTrigger++
                }
            }
            onDispose { job.cancel() }
        }
    }

    @Composable
    private fun BatteryOptimizationCard() {
        val context = LocalContext.current
        var isIgnoringBatteryOptimizations by remember {
            mutableStateOf(checkBatteryOptimizations(context))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isIgnoringBatteryOptimizations)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isIgnoringBatteryOptimizations) "\u2713 " else "\u26A0 ",
                        color = if (isIgnoringBatteryOptimizations)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = "Battery Optimization",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                        color = if (isIgnoringBatteryOptimizations)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (isIgnoringBatteryOptimizations) "Excluded" else "Optimizing",
                        fontWeight = FontWeight.Medium,
                        color = if (isIgnoringBatteryOptimizations)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isIgnoringBatteryOptimizations)
                        "GameShift is excluded from battery optimizations. This ensures background monitoring is not killed by the OS."
                    else
                        "GameShift is subject to battery optimization and may be killed in the background. Please exclude it to ensure reliable auto-switching.",
                    fontSize = 12.sp,
                    color = if (isIgnoringBatteryOptimizations)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
                if (!isIgnoringBatteryOptimizations) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    Log.e(TAG, "Failed to open battery optimization settings", ex)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Exclude App", fontSize = 13.sp)
                    }
                }
            }
        }
        
        DisposableEffect(Unit) {
            val job = scope.launch {
                while (isActive) {
                    delay(2000)
                    isIgnoringBatteryOptimizations = checkBatteryOptimizations(context)
                }
            }
            onDispose { job.cancel() }
        }
    }

    private fun checkBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    @Composable
    private fun StatusCard(status: AppStatus) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatusRow("Controller", status.controllerConnected, status.controllers.joinToString(", "))
                Spacer(Modifier.height(4.dp))
                StatusRow("Shizuku", status.shizukuReady, "")
                Spacer(Modifier.height(4.dp))
                StatusRow("Service", status.serviceRunning, "")
            }
        }
    }

    @Composable
    private fun StatusRow(label: String, ok: Boolean, detail: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (ok) "\u2713 " else "\u25CB ",
                color = if (ok)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 16.sp,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = label,
                modifier = Modifier.width(120.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (ok) "Active" else "Off",
                fontWeight = FontWeight.Medium,
                color = if (ok) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (detail.isNotBlank()) {
                Text(
                    text = " \u00B7 $detail",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun RotationModeSelector(
        currentMode: Int,
        onModeSelected: (Int) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Game Mode Rotation",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Orientation forced when game launcher is active",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple(0, "Retain Rotation", 0.3f),
                        Triple(1, "90° Landscape", 0.35f),
                        Triple(3, "-90° Reverse", 0.35f)
                    )
                    modes.forEach { (mode, label, weight) ->
                        val selected = currentMode == mode
                        Button(
                            onClick = { onModeSelected(mode) },
                            modifier = Modifier.weight(weight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                contentColor = if (selected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LauncherSelector(
        title: String,
        description: String,
        currentPackage: String?,
        onSelect: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(description, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentPackage?.let { getAppName(this@MainActivity, it) }
                            ?: stringResource(R.string.onb_config_not_set),
                        modifier = Modifier.weight(1f),
                        color = if (currentPackage != null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onSelect) {
                        Text(stringResource(R.string.btn_select))
                    }
                }
            }
        }
    }

    /**
     * Show a dialog to pick from installed launcher apps.
     */
    private fun showLauncherPickerDialog(requestCode: Int, onSelected: (String) -> Unit) {
        val launcherApps = getInstalledLauncherPackages()
        if (launcherApps.isEmpty()) {
            Toast.makeText(this, "No launcher apps found", Toast.LENGTH_SHORT).show()
            return
        }

        val items = launcherApps.map { getAppName(this@MainActivity, it) to it }.toTypedArray()
        val names = items.map { it.first }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle(if (requestCode == GAME_LAUNCHER_REQUEST) "Select Game Launcher" else "Select Normal Launcher")
            .setItems(names) { _, which ->
                val packageName = items[which].second
                if (requestCode == GAME_LAUNCHER_REQUEST) {
                    prefs.gameLauncherPackage = packageName
                } else {
                    prefs.normalLauncherPackage = packageName
                }
                onSelected(packageName)
            }
            .show()
    }

    /**
     * Find all installed apps that are valid launchers (have MAIN/HOME intent filter).
     */
    private fun getInstalledLauncherPackages(): List<String> {
        return getHomeLauncherPackages(this, packageName).map { it.second }
    }

    /**
     * Check whether the controller monitoring service is currently running.
     */
    private fun isServiceRunning(): Boolean {
        return try {
            val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            manager?.getRunningServices(Integer.MAX_VALUE)
                ?.any { it.service?.className == ControllerMonitorService::class.qualifiedName } == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check service running state", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val GAME_LAUNCHER_REQUEST = 1001
        private const val NORMAL_LAUNCHER_REQUEST = 1002
        private const val TAG = "MainActivity"
    }
}

/** Simple data class for the status display. */
private data class AppStatus(
    val controllerConnected: Boolean,
    val controllers: List<String>,
    val shizukuReady: Boolean,
    val serviceRunning: Boolean,
    val gameLauncher: String?,
    val normalLauncher: String?
)
