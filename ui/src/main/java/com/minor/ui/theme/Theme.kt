package com.minor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MeshGreen,
    onPrimary = MeshBackground,
    secondary = MeshAccentBlue,
    onSecondary = Color.White,
    tertiary = MeshGreenDark,
    background = MeshBackground,
    onBackground = MeshTextPrimary,
    surface = MeshSurface,
    onSurface = MeshTextPrimary,
    surfaceVariant = MeshHeader,
    onSurfaceVariant = MeshMuted,
    outline = MeshBorder
)

private val LightColorScheme = lightColorScheme(
    primary = MeshAccentBlue,
    onPrimary = Color.White,
    secondary = MeshGreenDark,
    background = Color(0xFFF4F7FC),
    surface = Color.White,
    onSurface = Color(0xFF0E1424)
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