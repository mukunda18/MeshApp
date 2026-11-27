package com.meshapp.meshapp

import android.content.Context
import com.meshapp.ui.state.SoundFeedbackSettings

internal class SoundPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): SoundFeedbackSettings =
        SoundFeedbackSettings(
            messageSoundsEnabled = preferences.getBoolean(MESSAGE_SOUNDS_ENABLED, true),
            callRingtonesEnabled = preferences.getBoolean(CALL_RINGTONES_ENABLED, true)
        )

    fun save(settings: SoundFeedbackSettings) {
        preferences.edit()
            .putBoolean(MESSAGE_SOUNDS_ENABLED, settings.messageSoundsEnabled)
            .putBoolean(CALL_RINGTONES_ENABLED, settings.callRingtonesEnabled)
            .apply()
    }

    fun areMessageSoundsEnabled(): Boolean =
        preferences.getBoolean(MESSAGE_SOUNDS_ENABLED, true)

    fun areCallRingtonesEnabled(): Boolean =
        preferences.getBoolean(CALL_RINGTONES_ENABLED, true)

    private companion object {
        const val PREFERENCES_NAME = "mesh_sound_preferences"
        const val MESSAGE_SOUNDS_ENABLED = "message_sounds_enabled"
        const val CALL_RINGTONES_ENABLED = "call_ringtones_enabled"
    }
}
