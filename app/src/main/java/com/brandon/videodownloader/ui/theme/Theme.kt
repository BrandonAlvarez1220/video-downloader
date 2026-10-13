package com.brandon.videodownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brandon.videodownloader.data.Settings.ThemeMode

/*
 * Sistema de diseño: en vez de colores sueltos por toda la app, se definen "tokens"
 * (primary, surface, surfaceContainer…) y los componentes los consumen. Cambiar el look
 * completo = cambiar este archivo. Es la misma idea que las variables CSS o un ResourceDictionary de XAML.
 */

object Brand {
    val Violet = Color(0xFF7C5CFF)
    val Pink = Color(0xFFFF4D8D)
    val Orange = Color(0xFFFF9F43)
    val Green = Color(0xFF2ED47A)

    /** Degradado de marca: botones principales, logo, íconos de estados vacíos. */
    val gradient = Brush.linearGradient(listOf(Violet, Pink))
    val gradientSoft = Brush.linearGradient(listOf(Violet.copy(alpha = 0.18f), Pink.copy(alpha = 0.18f)))
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D86FF),
    onPrimary = Color(0xFF1A0B4D),
    primaryContainer = Color(0xFF3A2A8C),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFFFF6FA3),
    onSecondary = Color(0xFF3D0018),
    tertiary = Brand.Green,
    background = Color(0xFF0B0B12),
    onBackground = Color(0xFFEDEDF6),
    surface = Color(0xFF0B0B12),
    onSurface = Color(0xFFEDEDF6),
    surfaceVariant = Color(0xFF22222F),
    onSurfaceVariant = Color(0xFFA3A3BA),
    surfaceContainerLowest = Color(0xFF08080D),
    surfaceContainerLow = Color(0xFF111119),
    surfaceContainer = Color(0xFF16161F),
    surfaceContainerHigh = Color(0xFF1E1E2A),
    surfaceContainerHighest = Color(0xFF282836),
    outline = Color(0xFF3A3A4E),
    outlineVariant = Color(0xFF2A2A3A),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF3B1418),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3DF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1A0B4D),
    secondary = Color(0xFFE23D78),
    tertiary = Color(0xFF1FA463),
    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF15151E),
    surface = Color(0xFFF6F6FB),
    onSurface = Color(0xFF15151E),
    surfaceVariant = Color(0xFFE9E9F2),
    onSurfaceVariant = Color(0xFF5E5E73),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFBFE),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F0F7),
    surfaceContainerHighest = Color(0xFFE8E8F1),
    outline = Color(0xFFD0D0DE),
    outlineVariant = Color(0xFFE4E4EE),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
