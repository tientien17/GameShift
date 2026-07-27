package com.gameshift.app.onboarding

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gameshift.app.GameShiftApp
import com.gameshift.app.MainActivity
import com.gameshift.app.service.ControllerMonitorService
import rikka.shizuku.Shizuku
import com.gameshift.app.theme.GameShiftTheme
import com.gameshift.app.theme.GameShiftColors
import com.gameshift.app.util.getAppName
import com.gameshift.app.util.isAppInstalled
import com.gameshift.app.util.getHomeLauncherPackages
import android.util.Log

@OptIn(ExperimentalFoundationApi::class)
class OnboardingActivity : ComponentActivity() {
 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (GameShiftApp.instance.prefs.onboardingCompleted) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }

        setContent {
            GameShiftTheme {
                OnboardingContent()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingContent() {
    val context = LocalContext.current
    var currentPage by rememberSaveable { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 7 }, initialPage = currentPage)

    // Sync currentPage with pagerState bidirectionally
    LaunchedEffect(pagerState.currentPage) {
        currentPage = pagerState.currentPage
    }
    LaunchedEffect(currentPage) {
        pagerState.animateScrollToPage(currentPage)
    }

    // ── State ──
    var shizukuGranted by rememberSaveable { mutableStateOf(false) }
    var serviceRunning by rememberSaveable { mutableStateOf(false) }

    var selectedGameLauncher by rememberSaveable {
        mutableStateOf(GameShiftApp.instance.prefs.gameLauncherPackage)
    }
    var selectedNormalLauncher by rememberSaveable {
        mutableStateOf(GameShiftApp.instance.prefs.normalLauncherPackage)
    }

    var showGameLauncherDialog by remember { mutableStateOf(false) }
    var showNormalLauncherDialog by remember { mutableStateOf(false) }

    // ── Shizuku permission result listener ──
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                shizukuGranted = true
                GameShiftApp.instance.prefs.shizukuAuthorized = true
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    // ── Skip helper ──
    val skipToEnd: () -> Unit = {
        if (currentPage <= 2) {
            currentPage = 6
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Main content area with pager ──
            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> WelcomePage()
                        1 -> HowItWorksPage()
                        2 -> ShizukuIntroPage()
                        3 -> ActivateShizukuPage()
                        4 -> GrantPermissionPage(
                            shizukuGranted = shizukuGranted,
                            onRequestPermission = {
                                try {
                                    if (Shizuku.pingBinder()) {
                                        Shizuku.requestPermission(0)
                                    }
                                } catch (e: Exception) {
                                    Log.e("Onboarding", "Shizuku not available", e)
                                }
                            }
                        )
                         5 -> ConfigureLaunchersPage(
                            gameLauncher = selectedGameLauncher,
                            normalLauncher = selectedNormalLauncher,
                            onSelectGameLauncher = {
                                showGameLauncherDialog = true
                            },
                            onSelectNormalLauncher = {
                                showNormalLauncherDialog = true
                            }
                        )
                        6 -> StartServiceDonePage(
                            serviceRunning = serviceRunning,
                            onStartService = {
                                try {
                                    ControllerMonitorService.start(context)
                                    serviceRunning = true
                                    GameShiftApp.instance.prefs.onboardingCompleted = true
                                } catch (e: Exception) {
                                    Log.e("Onboarding", "Failed to start service", e)
                                }
                            },
                            onGetStarted = {
                                GameShiftApp.instance.prefs.onboardingCompleted = true
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                context.startActivity(intent)
                                (context as? ComponentActivity)?.finish()
                            }
                        )
                    }
                }
            }

            // ── Bottom bar: dots + navigation buttons ──
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Dot indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(7) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (currentPage == index) 14.dp else 10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (currentPage == index)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    .semantics {
                                        contentDescription = "Page ${index + 1} of 7${if (currentPage == index) ", current page" else ""}"
                                    }
                            )
                        }
                    }
                    Text(
                        text = "${currentPage + 1} / 7",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Navigation buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip - on info pages only (pages 0-2)
                        if (currentPage < 6 && currentPage <= 2) {
                            TextButton(onClick = skipToEnd) {
                                Text(
                                    stringResource(com.gameshift.app.R.string.onboarding_skip),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else if (currentPage < 6) {
                            // Spacer to keep Next aligned right
                            Spacer(Modifier.width(1.dp))
                        }

                        Spacer(Modifier.weight(1f))

                        // Next or Get Started
                        if (currentPage < 6) {
                            Button(
                                onClick = { currentPage++ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(stringResource(com.gameshift.app.R.string.onboarding_next))
                            }
                        } else {
                            Button(
                                onClick = {
                                    GameShiftApp.instance.prefs.onboardingCompleted = true
                                    try {
                                        ControllerMonitorService.start(context)
                                    } catch (e: Exception) {
                                        Log.e("Onboarding", "Failed to start service on finish", e)
                                    }
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    context.startActivity(intent)
                                    (context as? ComponentActivity)?.finish()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(stringResource(com.gameshift.app.R.string.onboarding_complete))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Launcher picker dialogs ──
    if (showGameLauncherDialog) {
        LauncherPickerDialog(
            title = stringResource(com.gameshift.app.R.string.onb_config_select),
            onSelect = { packageName ->
                GameShiftApp.instance.prefs.gameLauncherPackage = packageName
                selectedGameLauncher = packageName
                showGameLauncherDialog = false
            },
            onDismiss = { showGameLauncherDialog = false }
        )
    }

    if (showNormalLauncherDialog) {
        LauncherPickerDialog(
            title = stringResource(com.gameshift.app.R.string.onb_config_select),
            onSelect = { packageName ->
                GameShiftApp.instance.prefs.normalLauncherPackage = packageName
                selectedNormalLauncher = packageName
                showNormalLauncherDialog = false
            },
            onDismiss = { showNormalLauncherDialog = false }
        )
    }
}

// ═══════════════════════════════════════════════════
//  Page Composables
// ═══════════════════════════════════════════════════

@Composable
private fun WelcomePage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = com.gameshift.app.R.mipmap.ic_launcher_foreground),
            contentDescription = "GameShift logo",
            modifier = Modifier.size(120.dp),
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(com.gameshift.app.R.string.onb_welcome_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(com.gameshift.app.R.string.onb_welcome_subtitle),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        // Feature bullets
        FeatureBullet(
            icon = Icons.Default.Search,
            text = stringResource(com.gameshift.app.R.string.onb_welcome_feature1)
        )
        Spacer(Modifier.height(14.dp))
        FeatureBullet(
            icon = Icons.Default.Refresh,
            text = stringResource(com.gameshift.app.R.string.onb_welcome_feature2)
        )
        Spacer(Modifier.height(14.dp))
        FeatureBullet(
            icon = Icons.Default.PlayArrow,
            text = stringResource(com.gameshift.app.R.string.onb_welcome_feature3)
        )
    }
}

@Composable
private fun HowItWorksPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(com.gameshift.app.R.string.onb_how_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        HowItWorksStep(
            stepNumber = 1,
            icon = Icons.Default.PlayArrow,
            title = stringResource(com.gameshift.app.R.string.onb_how_step1),
            description = "Pair any Bluetooth or USB game controller" // TODO: extract to strings.xml
        )
        Spacer(Modifier.height(20.dp))
        HowItWorksStep(
            stepNumber = 2,
            icon = Icons.Default.Search,
            title = stringResource(com.gameshift.app.R.string.onb_how_step2),
            description = "Background monitoring detects the connection" // TODO: extract to strings.xml
        )
        Spacer(Modifier.height(20.dp))
        HowItWorksStep(
            stepNumber = 3,
            icon = Icons.Default.Home,
            title = "Press HOME", // TODO: extract to strings.xml
            description = "Your game launcher opens automatically" // TODO: extract to strings.xml
        )
        Spacer(Modifier.height(20.dp))
        HowItWorksStep(
            stepNumber = 4,
            icon = Icons.Default.Close,
            title = "Disconnect the controller", // TODO: extract to strings.xml
            description = "Your normal launcher is restored" // TODO: extract to strings.xml
        )
    }
}

@Composable
private fun ShizukuIntroPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(com.gameshift.app.R.string.onb_shizuku_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = stringResource(com.gameshift.app.R.string.onb_shizuku_desc),
                modifier = Modifier.padding(16.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 21.sp
            )
        }

        val isInstalled = remember { isAppInstalled(context, "moe.shizuku.privileged.api") }

        if (isInstalled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Check mark indicating Shizuku is installed",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Shizuku is installed", // TODO: extract to strings.xml
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=moe.shizuku.privileged.api")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("Onboarding", "Failed to open Play Store for Shizuku", e)
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                            )
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            Log.e("Onboarding", "Failed to open Shizuku URL", e2)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(com.gameshift.app.R.string.onb_shizuku_playstore))
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("Onboarding", "Failed to open Shizuku GitHub", e)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download from GitHub") // TODO: extract to strings.xml
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(com.gameshift.app.R.string.onb_shizuku_already),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActivateShizukuPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedMethod by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(com.gameshift.app.R.string.onb_activate_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        if (selectedMethod == null) {
            Text(
                text = "Choose your setup method:", // TODO: extract to strings.xml
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(16.dp))

            Card(
                onClick = { selectedMethod = 1 },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "I don't have root", // TODO: extract to strings.xml
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Use Wireless Debugging — no PC needed", // TODO: extract to strings.xml
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                onClick = { selectedMethod = 2 },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "I have root", // TODO: extract to strings.xml
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Enable Shizuku directly in the Shizuku app", // TODO: extract to strings.xml
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else if (selectedMethod == 1) {
            val devOptionsEnabled = remember {
                try {
                    Settings.Global.getInt(
                        context.contentResolver,
                        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                        0
                    ) == 1
                } catch (e: Exception) {
                    Log.e("Onboarding", "Failed to check dev options", e)
                    false
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Step-by-step", // TODO: extract to strings.xml
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "1. Open Shizuku app\n" +
                                "2. Tap \"Start\" and select \"Wireless debugging\"\n" +
                                "3. Shizuku will guide you through the pairing process\n" +
                                "4. Once Shizuku shows \"Running\", return here", // TODO: extract to strings.xml
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!devOptionsEnabled) {
                Button(
                    onClick = {
                        Toast.makeText(
                            context,
                            "Enable Developer Options first: Settings > About Phone > Tap Build Number 7 times", // TODO: extract to strings.xml
                            Toast.LENGTH_LONG
                        ).show()
                        try {
                            context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                        } catch (e: Exception) {
                            Log.e("Onboarding", "Failed to open device info settings", e)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Enable Developer Options", fontSize = 15.sp) // TODO: extract to strings.xml
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Developer Options must be enabled for Wireless Debugging", // TODO: extract to strings.xml
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
                } else {
                Button(
                    onClick = {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (intent != null) context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("Onboarding", "Failed to open Shizuku app", e)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Open Shizuku App", fontSize = 15.sp) // TODO: extract to strings.xml
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wireless Debugging must be ON in ", // TODO: extract to strings.xml
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            } catch (e: Exception) {
                                Log.e("Onboarding", "Failed to open developer settings", e)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("Developer Options", fontSize = 12.sp) // TODO: extract to strings.xml
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { selectedMethod = null }) {
                Text("Choose a different method", fontSize = 13.sp) // TODO: extract to strings.xml
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Enable Shizuku in the Shizuku app", // TODO: extract to strings.xml
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "1. Open the Shizuku app\n" +
                                "2. Tap \"Start\" under the Root method\n" +
                                "3. Grant superuser access when prompted", // TODO: extract to strings.xml
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { selectedMethod = null }) {
                Text("Choose a different method", fontSize = 13.sp) // TODO: extract to strings.xml
            }
        }
    }
}

@Composable
private fun GrantPermissionPage(
    shizukuGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(com.gameshift.app.R.string.onb_perm_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = stringResource(com.gameshift.app.R.string.onb_perm_desc),
                modifier = Modifier.padding(16.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 21.sp
            )
        }

        Spacer(Modifier.height(28.dp))

        if (shizukuGranted) {
            // Permission granted — show checkmark
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Check mark indicating permission granted",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(com.gameshift.app.R.string.onb_perm_granted),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(com.gameshift.app.R.string.onb_perm_grant), fontSize = 15.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(com.gameshift.app.R.string.onb_perm_fallback),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun ConfigureLaunchersPage(
    gameLauncher: String?,
    normalLauncher: String?,
    onSelectGameLauncher: () -> Unit,
    onSelectNormalLauncher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(com.gameshift.app.R.string.onb_config_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // ── Game Launcher ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(com.gameshift.app.R.string.onb_config_game_label),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(com.gameshift.app.R.string.onb_config_game_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = gameLauncher?.let { getAppName(context, it) } ?: stringResource(com.gameshift.app.R.string.onb_config_not_set),
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        color = if (gameLauncher != null)
                            MaterialTheme.colorScheme.primary
                        else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Button(
                        onClick = onSelectGameLauncher,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(com.gameshift.app.R.string.btn_select), fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Normal Launcher ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(com.gameshift.app.R.string.onb_config_normal_label),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(com.gameshift.app.R.string.onb_config_normal_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = normalLauncher?.let { getAppName(context, it) } ?: stringResource(com.gameshift.app.R.string.onb_config_not_set),
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        color = if (normalLauncher != null)
                            MaterialTheme.colorScheme.primary
                        else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Button(
                        onClick = onSelectNormalLauncher,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(com.gameshift.app.R.string.btn_select), fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(com.gameshift.app.R.string.onb_config_note),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StartServiceDonePage(
    serviceRunning: Boolean,
    onStartService: () -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isStarting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Mascot
        Image(
            painter = painterResource(id = com.gameshift.app.R.drawable.ic_mascot_chatgpt),
            contentDescription = "GameShift mascot",
            modifier = Modifier.size(120.dp),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(com.gameshift.app.R.string.onb_done_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(com.gameshift.app.R.string.onb_done_desc),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )

        Spacer(Modifier.height(28.dp))

        if (serviceRunning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Check mark indicating service is running",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(com.gameshift.app.R.string.onb_done_running),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(com.gameshift.app.R.string.onboarding_complete), fontSize = 16.sp)
            }
        } else {
            Button(
                onClick = {
                    isStarting = true
                    onStartService()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !isStarting
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(com.gameshift.app.R.string.btn_start), fontSize = 16.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  Reusable helper composables
// ═══════════════════════════════════════════════════

@Composable
private fun FeatureBullet(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun HowItWorksStep(
    stepNumber: Int,
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Step number circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$stepNumber",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun LauncherPickerDialog(
    title: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentDefault = remember {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        try {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        } catch (e: Exception) {
            Log.e("Onboarding", "Failed to resolve default launcher", e)
            null
        }
    }
    val launcherItems = remember {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
        resolveInfos
            .map { it.activityInfo.packageName }
            .distinct()
            .filterNot { it == context.packageName }
            .sorted()
            .map { pkg ->
                val ai = try {
                    context.packageManager.getApplicationInfo(pkg, 0)
                } catch (e: Exception) {
                    Log.e("Onboarding", "Failed to get app info for $pkg", e)
                    null
                }
                val name = ai?.let { context.packageManager.getApplicationLabel(it).toString() }
                    ?: pkg
                name to pkg
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            if (launcherItems.isEmpty()) {
                Text(
                    text = "No launcher apps found.", // TODO: extract to strings.xml
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                Column {
                    launcherItems.forEach { (name, pkg) ->
                        val isDefault = pkg == currentDefault
                        TextButton(
                            onClick = { onSelect(pkg) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start
                                )
                                if (isDefault) {
                                    Text(
                                        text = "Default", // TODO: extract to strings.xml
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel") // TODO: extract to strings.xml
            }
        }
    )
}


