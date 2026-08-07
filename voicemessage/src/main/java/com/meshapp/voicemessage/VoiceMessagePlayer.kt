package com.meshapp.voicemessage

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.meshapp.logger.MeshLogger
import com.meshapp.voice.VoiceCodec
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Plays back a mu-law encoded voice message file produced by
 * [VoiceMessageRecorder], reusing the same [VoiceCodec] frame decoding used
 * for live call playback.
 */
class VoiceMessagePlayer(
    private val codec: VoiceCodec = VoiceCodec()
) {
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    val isPlaying: Boolean
        get() = playbackJob?.isActive == true

    fun play(file: File, onComplete: () -> Unit = {}) {
        if (isPlaying) stop()
        if (!file.exists()) {
            MeshLogger.error("VoiceMessagePlayer", "File does not exist: ${file.absolutePath}")
            return
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            VoiceCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            VoiceCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, VoiceCodec.BYTES_PER_FRAME * 4),
            AudioTrack.MODE_STREAM
        )
        audioTrack = track
        track.play()

        playbackJob = scope.launch {
            file.inputStream().use { input ->
                val compressedFrame = ByteArray(VoiceCodec.COMPRESSED_FRAME_SIZE)
                while (isPlaying) {
                    val bytesRead = input.read(compressedFrame)
                    if (bytesRead != VoiceCodec.COMPRESSED_FRAME_SIZE) break
                    val pcm = codec.decode(compressedFrame)
                    track.write(pcm, 0, pcm.size)
                }
            }
            track.stop()
            track.release()
            audioTrack = null
            onComplete()
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null
    }

    fun release() {
        codec.release()
    }
}
