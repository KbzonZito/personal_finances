package com.personalfinances.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalfinances.data.prefs.ThemeMode

@Immutable
data class FinanceColors(val income: Color, val expense: Color, val warning: Color, val muted: Color)

val LocalFinanceColors = staticCompositionLocalOf { FinanceColors(Color.Green, Color.Red, Color.Yellow, Color.Gray) }

private val Dark = darkColorScheme(
    primary = Color(0xFF9D8CFF),
    onPrimary = Color(0xFF14102E),
    primaryContainer = Color(0xFF2B2550),
    onPrimaryContainer = Color(0xFFE3DEFF),
    secondary = Color(0xFF5EE2A0),
    secondaryContainer = Color(0xFF1C2B26),
    onSecondaryContainer = Color(0xFFBFF5DA),
    background = Color(0xFF0D0E12),
    onBackground = Color(0xFFF1F1F4),
    surface = Color(0xFF0D0E12),
    onSurface = Color(0xFFF1F1F4),
    surfaceVariant = Color(0xFF1B1D23),
    onSurfaceVariant = Color(0xFF9A9CA8),
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF14161B),
    surfaceContainer = Color(0xFF181A20),
    surfaceContainerHigh = Color(0xFF1F2128),
    surfaceContainerHighest = Color(0xFF272A32),
    outline = Color(0xFF3A3D47),
    outlineVariant = Color(0xFF26282F),
    error = Color(0xFFFF6B7A),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B47E0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E5FF),
    onPrimaryContainer = Color(0xFF1E1466),
    secondary = Color(0xFF0E9F6E),
    secondaryContainer = Color(0xFFDDF6EB),
    onSecondaryContainer = Color(0xFF053D2A),
    background = Color(0xFFF6F6F9),
    onBackground = Color(0xFF111217),
    surface = Color(0xFFF6F6F9),
    onSurface = Color(0xFF111217),
    surfaceVariant = Color(0xFFECEDF2),
    onSurfaceVariant = Color(0xFF6B6E7B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F0F5),
    surfaceContainerHigh = Color(0xFFEAEAF0),
    surfaceContainerHighest = Color(0xFFE3E4EB),
    outline = Color(0xFFCDCFD8),
    outlineVariant = Color(0xFFE4E5EC),
    error = Color(0xFFD93A4F),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val AmountStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FinanceTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val fc = if (dark) FinanceColors(Color(0xFF5EE2A0), Color(0xFFFF7A88), Color(0xFFFFC960), Color(0xFF6E7180))
    else FinanceColors(Color(0xFF0E9F6E), Color(0xFFD93A4F), Color(0xFFE09B00), Color(0xFF9A9CA8))
    androidx.compose.runtime.CompositionLocalProvider(LocalFinanceColors provides fc) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
