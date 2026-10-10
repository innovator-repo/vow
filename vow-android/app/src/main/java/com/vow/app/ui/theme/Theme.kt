package com.vow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = VowTeal,
    onPrimary = Color(0xFF04201C),
    primaryContainer = Color(0xFF0F2F2C),
    onPrimaryContainer = Color(0xFFA7F3E4),
    secondary = VowAmber,
    onSecondary = Color(0xFF2A1D00),
    secondaryContainer = Color(0xFF33270C),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = VowSeal,
    onTertiary = Color(0xFF1E1240),
    tertiaryContainer = Color(0xFF251B45),
    onTertiaryContainer = Color(0xFFE3D9FF),
    background = Ink,
    onBackground = InkText,
    surface = Ink,
    onSurface = InkText,
    surfaceVariant = InkSurface,
    onSurfaceVariant = InkTextMuted,
    surfaceTint = Color.Transparent,
    error = VowRose,
    onError = Color(0xFF3B0A12),
    errorContainer = Color(0xFF3B1520),
    onErrorContainer = Color(0xFFFFD6DC),
    outline = InkLine,
    outlineVariant = Color(0xFF1C2532),
    inverseSurface = InkText,
    inverseOnSurface = Ink
)

private val LightColorScheme = lightColorScheme(
    primary = VowTealDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = VowSealDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF3B0764),
    background = Paper,
    onBackground = PaperText,
    surface = Paper,
    onSurface = PaperText,
    surfaceVariant = PaperSurface,
    onSurfaceVariant = PaperTextMuted,
    surfaceTint = Color.Transparent,
    error = Color(0xFFE11D48),
    errorContainer = Color(0xFFFFE4E6),
    outline = PaperLine,
    outlineVariant = PaperSurfaceHigh
)

private val VowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun VowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VowTypography,
        shapes = VowShapes,
        content = content
    )
}
