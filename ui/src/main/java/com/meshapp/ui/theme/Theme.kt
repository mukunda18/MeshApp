package com.meshapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MeshGreen,
    onPrimary = MeshGreenOnAccent,
    primaryContainer = MeshGreenMuted,
    onPrimaryContainer = MeshGreen,
    secondary = MeshGreenDark,
    onSecondary = Color.White,
    background = MeshBg0,
    onBackground = MeshTextPrimary,
    surface = MeshBg1,
    onSurface = MeshTextPrimary,
    surfaceVariant = MeshBg2,
    onSurfaceVariant = MeshTextSecondary,
    outline = MeshBorder,
    outlineVariant = MeshDivider,
    error = MeshDanger,
    onError = Color.White,
    errorContainer = MeshDangerMuted,
    onErrorContainer = MeshDanger
)

private val LightColorScheme = lightColorScheme(
    primary = MeshGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3E6),
    onPrimaryContainer = MeshGreenDark,
    secondary = MeshGreenDark,
    background = MeshLightBg0,
    onBackground = MeshLightTextPrimary,
    surface = MeshLightBg1,
    onSurface = MeshLightTextPrimary,
    surfaceVariant = Color(0xFFEFF3F1),
    onSurfaceVariant = MeshLightTextSecondary,
    outline = MeshLightBorder,
    error = MeshDanger,
    onError = Color.White
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
