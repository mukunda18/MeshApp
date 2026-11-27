package com.meshapp.ui

import com.meshapp.ui.theme.MeshThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun missingOrUnknownPreferenceDefaultsToSystem() {
        assertEquals(MeshThemeMode.System, MeshThemeMode.fromPreference(null))
        assertEquals(MeshThemeMode.System, MeshThemeMode.fromPreference("invalid"))
    }

    @Test
    fun savedModesCanBeRestored() {
        MeshThemeMode.entries.forEach { mode ->
            assertEquals(mode, MeshThemeMode.fromPreference(mode.name))
        }
    }
}
