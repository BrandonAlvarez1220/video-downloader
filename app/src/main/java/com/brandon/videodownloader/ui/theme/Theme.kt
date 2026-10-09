package com.brandon.videodownloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Purple = Color(0xFF6C3BFF)

private val Dark = darkColorScheme(
    primary = Color(0xFFB9A4FF),
    onPrimary = Color(0xFF2A0F7A),
    primaryContainer = Color(0xFF4521C9),
    background = Color(0xFF101014),
    surface = Color(0xFF101014),
)

private val Light = lightColorScheme(
    primary = Purple,
    primaryContainer = Color(0xFFE6DEFF),
)

/** Usa los colores del fondo de pantalla del usuario en Android 12+ (Material You). */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
