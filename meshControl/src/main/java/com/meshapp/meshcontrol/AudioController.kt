package com.meshapp.meshcontrol

import android.content.Context
import android.media.AudioManager
import com.meshapp.logger.MeshLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioSessionType(val priority: Int) {
    VOICE_CALL(3),
    VOICE_MESSAGE(2),
    LOOPBACK(1),
    NONE(0)
}

/**
 * Central manager for all audio sessions in the Mesh app.
 * Enforces priority preemption: Voice Call > Voice Message > Loopback.
 */
class AudioController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private val _activeSession = MutableStateFlow(AudioSessionType.NONE)
    val activeSession = _activeSession.asStateFlow()

    private var activeStopper: (() -> Unit)? = null

    /**
     * Attempts to start a new audio session. 
     * If a higher priority session is active, returns false.
     * If a lower priority session is active, it is preempted (stopped).
     */
    @Synchronized
    fun startSession(type: AudioSessionType, mode: Int? = null, stopper: () -> Unit): Boolean {
        val current = _activeSession.value
        
        if (current != AudioSessionType.NONE) {
            if (type.priority < current.priority) {
                MeshLogger.info("AudioController", "Cannot start $type: $current is active with higher priority")
                return false
            }
            if (type.priority == current.priority && type != current) {
                // Same priority but different type
                MeshLogger.info("AudioController", "Preempting $current for new $type session")
            }
        }

        // Preempt current session if any
        stopActiveSessionInternal("Preempted by $type")

        MeshLogger.info("AudioController", "Starting $type session")
        _activeSession.value = type
        activeStopper = stopper
        
        // Mode will be set by the individual managers, but we use the provided one or a safe base.
        audioManager.mode = mode ?: when (type) {
            AudioSessionType.VOICE_CALL, AudioSessionType.LOOPBACK -> AudioManager.MODE_IN_COMMUNICATION
            AudioSessionType.VOICE_MESSAGE -> AudioManager.MODE_NORMAL
            else -> AudioManager.MODE_NORMAL
        }
        
        return true
    }

    @Synchronized
    fun stopSession(type: AudioSessionType) {
        if (_activeSession.value == type) {
            stopActiveSessionInternal("Natural stop")
        }
    }

    @Synchronized
    fun stopAll() {
        MeshLogger.info("AudioController", "Emergency stop for all audio sessions")
        stopActiveSessionInternal("Mesh shutdown")
    }

    private fun stopActiveSessionInternal(reason: String) {
        val type = _activeSession.value
        if (type == AudioSessionType.NONE) return
        
        MeshLogger.info("AudioController", "Stopping $type session. Reason: $reason")
        
        val stopper = activeStopper
        activeStopper = null
        _activeSession.value = AudioSessionType.NONE
        
        stopper?.invoke()
        
        // Reset hardware mode
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
