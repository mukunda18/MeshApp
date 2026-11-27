package com.meshapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

const val THEME_PREFERENCES = "mesh_theme_preferences"
const val THEME_MODE_KEY = "theme_mode"

enum class MeshThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark");

    companion object {
        fun fromPreference(value: String?): MeshThemeMode =
            entries.firstOrNull { it.name == value } ?: System
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF61D6A0),
    onPrimary = Color(0xFF052B1B),
    primaryContainer = Color(0xFF16432F),
    onPrimaryContainer = Color(0xFFB7F5D7),
    secondary = Color(0xFFA7D0B7),
    onSecondary = Color(0xFF15372A),
    secondaryContainer = Color(0xFF234A38),
    onSecondaryContainer = Color(0xFFC1EBD0),
    tertiary = Color(0xFFE7C95E),
    onTertiary = Color(0xFF382D00),
    tertiaryContainer = Color(0xFF514500),
    onTertiaryContainer = Color(0xFFFFEFAE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE9ECEF),
    surface = Color(0xFF121519),
    onSurface = Color(0xFFE9ECEF),
    surfaceVariant = Color(0xFF252B30),
    onSurfaceVariant = Color(0xFFD0D9D3),
    outline = Color(0xFF909B95),
    outlineVariant = Color(0xFF707A74),
    inverseSurface = Color(0xFFE1E5E2),
    inverseOnSurface = Color(0xFF2E3330),
    inversePrimary = Color(0xFF176B4B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176B4B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC5EBD7),
    onPrimaryContainer = Color(0xFF092D20),
    secondary = Color(0xFF386052),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEFE4),
    onSecondaryContainer = Color(0xFF142D22),
    tertiary = Color(0xFF725B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E5A7),
    onTertiaryContainer = Color(0xFF251A00),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF14201B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14201B),
    surfaceVariant = Color(0xFFE9EFEB),
    onSurfaceVariant = Color(0xFF43534B),
    outline = Color(0xFF53635A),
    outlineVariant = Color(0xFF6C7C73),
    inverseSurface = Color(0xFF2E3330),
    inverseOnSurface = Color(0xFFEFF1EE),
    inversePrimary = Color(0xFF91D6B2)
)

@Composable
fun MeshAppTheme(
    themeMode: MeshThemeMode = MeshThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        MeshThemeMode.System -> isSystemInDarkTheme()
        MeshThemeMode.Light -> false
        MeshThemeMode.Dark -> true
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
