package com.minor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MeshGreen,
    onPrimary = Color.White,
    secondary = MeshGreenDark,
    background = Color(0xFF0B0F14),
    surface = Color(0xFF121821),
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = MeshGreen,
    onPrimary = Color.White,
    secondary = MeshGreenDark,
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    onSurface = Color(0xFF16212E)
)

@Composable
fun MeshAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
