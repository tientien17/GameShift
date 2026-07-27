package com.gameshift.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gameshift.app.R

/**
 * Shared retro arcade colour scheme for GameShift.
 *
 * Palette:
 *   Background  – #0A0A1A  (very dark blue-black)
 *   Surface      – #150A25  (dark purple)
 *   Primary      – #FF00FF  (neon magenta)
 *   Secondary    – #00FFFF  (cyan)
 *   Tertiary     – #FFFF00  (yellow)
 *
 * This composable replaces the inline MaterialTheme { } blocks previously
 * duplicated in MainActivity and OnboardingActivity. It also fills critical
 * colour slots (errorContainer, surfaceVariant, onSurfaceVariant, outline)
 * that the old inline scheme omitted.
 */

/** Retro arcade monospace font family — used for body text, headings. */
private val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/** Pixel-art display font — used for titles and decorative text. */
private val PressStart2P = FontFamily(Font(R.font.press_start_2p_regular))

/** Material3 Typography for the retro arcade aesthetic. */
private val GameShiftTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PressStart2P,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun GameShiftTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFFFF00FF),           // Neon magenta
        secondary = Color(0xFF00FFFF),          // Cyan
        tertiary = Color(0xFFFFFF00),           // Yellow
        background = Color(0xFF0A0A1A),         // Very dark blue-black
        surface = Color(0xFF150A25),             // Dark purple
        surfaceVariant = Color(0xFF1E1035),      // Slightly lighter purple
        error = Color(0xFFFF3333),               // Red
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFF000000),
        onBackground = Color(0xFFE0E0E0),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFC8B8D8),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFF4A1515),      // Dark red container
        onErrorContainer = Color(0xFFFFD0D0),
        outline = Color(0xFF3A2A4A),
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GameShiftTypography,
        content = content,
    )
}

/**
 * Raw theme colours for use outside composable scope or for one-off overrides.
 */
object GameShiftColors {
    val background = Color(0xFF0A0A1A)
    val surface = Color(0xFF150A25)
    val surfaceVariant = Color(0xFF1E1035)
    val primary = Color(0xFFFF00FF)
    val secondary = Color(0xFF00FFFF)
    val accent = Color(0xFFFFFF00)
    val error = Color(0xFFFF3333)
    val onBackground = Color(0xFFE0E0E0)
    val onSurface = Color(0xFFFFFFFF)

    // Semantic text colours with guaranteed contrast ratios
    val textPrimary = Color(0xFFFFFFFF)         // 15.4:1 on background
    val textSecondary = Color(0xFFB3B3C4)       //  8.5:1 on background
    val textDisabled = Color(0xFF80809C)         //  4.8:1 on background
    val textMuted = Color(0xFF59597A)            //  2.9:1 on background (decorative use only)
}
