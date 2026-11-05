package com.bettersms.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colour tokens for the frosted-glass look (inspired by vivo OriginOS). */
@Immutable
data class GlassColors(
    val isDark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val blobs: List<Color>,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassHighlight: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val dialog: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentEnd: Color,
    val onAccent: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val divider: Color,
)

val LightGlass = GlassColors(
    isDark = false,
    bgTop = Color(0xFFEAF0FF),
    bgBottom = Color(0xFFF6EEFF),
    blobs = listOf(Color(0x997FA6FF), Color(0x80C3A2FF), Color(0x7378E0F0), Color(0x5CFFB8DC)),
    glassFill = Color.White.copy(alpha = 0.55f),
    glassFillStrong = Color.White.copy(alpha = 0.78f),
    glassHighlight = Color.White.copy(alpha = 0.45f),
    glassBorderTop = Color.White.copy(alpha = 0.95f),
    glassBorderBottom = Color.White.copy(alpha = 0.30f),
    dialog = Color(0xFFF7F8FF),
    textPrimary = Color(0xFF121633),
    textSecondary = Color(0xFF4A4F6B),
    textTertiary = Color(0xFF7A7F99),
    accent = Color(0xFF415FFF),
    accentEnd = Color(0xFF7B5CFF),
    onAccent = Color.White,
    danger = Color(0xFFE5484D),
    warning = Color(0xFFE38A00),
    success = Color(0xFF12A877),
    divider = Color(0x1A121633),
)

val DarkGlass = GlassColors(
    isDark = true,
    bgTop = Color(0xFF090E22),
    bgBottom = Color(0xFF150E2C),
    blobs = listOf(Color(0x802F4BFF), Color(0x707C3AED), Color(0x590891B2), Color(0x3DDB2777)),
    glassFill = Color.White.copy(alpha = 0.07f),
    glassFillStrong = Color.White.copy(alpha = 0.13f),
    glassHighlight = Color.White.copy(alpha = 0.06f),
    glassBorderTop = Color.White.copy(alpha = 0.26f),
    glassBorderBottom = Color.White.copy(alpha = 0.05f),
    dialog = Color(0xFF1A1D36),
    textPrimary = Color(0xFFF2F4FF),
    textSecondary = Color(0xFFB7BCD9),
    textTertiary = Color(0xFF8187A8),
    accent = Color(0xFF6C84FF),
    accentEnd = Color(0xFF9B7BFF),
    onAccent = Color.White,
    danger = Color(0xFFFF6B70),
    warning = Color(0xFFFFB547),
    success = Color(0xFF3DD9A4),
    divider = Color(0x1FFFFFFF),
)

val LocalGlass = staticCompositionLocalOf { LightGlass }

object Glass {
    val colors: GlassColors
        @Composable get() = LocalGlass.current
}

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun BetterSmsTheme(themeMode: Int, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val g = if (dark) DarkGlass else LightGlass
    val scheme = if (dark) {
        darkColorScheme(
            primary = g.accent, onPrimary = g.onAccent, secondary = g.accentEnd,
            background = g.bgTop, surface = g.dialog, onSurface = g.textPrimary, onBackground = g.textPrimary,
            surfaceVariant = g.glassFillStrong, onSurfaceVariant = g.textSecondary, error = g.danger,
            surfaceContainer = g.dialog, surfaceContainerHigh = g.dialog, surfaceContainerHighest = g.dialog,
        )
    } else {
        lightColorScheme(
            primary = g.accent, onPrimary = g.onAccent, secondary = g.accentEnd,
            background = g.bgTop, surface = g.dialog, onSurface = g.textPrimary, onBackground = g.textPrimary,
            surfaceVariant = g.glassFillStrong, onSurfaceVariant = g.textSecondary, error = g.danger,
            surfaceContainer = g.dialog, surfaceContainerHigh = g.dialog, surfaceContainerHighest = g.dialog,
        )
    }
    CompositionLocalProvider(LocalGlass provides g) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
