package com.meshapp.voicemessage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.meshapp.logger.MeshLogger
import com.meshapp.voice.VoiceCodec
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Records a voice message using the same capture pipeline as live calls
 * (16 kHz mono PCM, mu-law compressed via VoiceCodec), but persists the
 * encoded frames to a local file instead of streaming them over UDP.
 *
 * Output is written using the naming convention in [VoiceMessageFile], so
 * FileTransferService can treat the result as an ordinary file with no
 * protocol changes.
 */
class VoiceMessageRecorder(
    private val context: Context,
    private val codec: VoiceCodec = VoiceCodec()
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var outputStream: FileOutputStream? = null
    private var outputFile: File? = null
    private var frameCount: Long = 0L

    val isRecording: Boolean
        get() = recordingJob?.isActive == true

    /**
     * Starts capturing audio into a temp file. Returns false if RECORD_AUDIO
     * has not been granted or a recording is already in progress.
     */
    fun start(): Boolean {
        if (isRecording) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            MeshLogger.error("VoiceMessageRecorder", "RECORD_AUDIO permission not granted")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            VoiceCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, VoiceCodec.BYTES_PER_FRAME * 4)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            VoiceCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            MeshLogger.error("VoiceMessageRecorder", "AudioRecord failed to initialize")
            record.release()
            return false
        }

        val file = VoiceMessageFile.newOutgoingTempFile(context)
        outputFile = file
        outputStream = FileOutputStream(file)
        audioRecord = record
        frameCount = 0L

        record.startRecording()

        recordingJob = scope.launch {
            val pcmBuffer = ByteArray(VoiceCodec.BYTES_PER_FRAME)
            while (isRecording) {
                val bytesRead = record.read(pcmBuffer, 0, pcmBuffer.size)
                if (bytesRead == VoiceCodec.BYTES_PER_FRAME) {
                    val encoded = codec.encode(pcmBuffer)
                    outputStream?.write(encoded)
                    frameCount++
                }
            }
        }
        return true
    }

    /**
     * Stops capture and finalizes the file. Returns the recorded voice
     * message (file + duration), or null if nothing usable was recorded.
     */
    fun stop(): RecordedVoiceMessage? {
        if (!isRecording) return null
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null

        outputStream?.flush()
        outputStream?.close()
        outputStream = null

        val durationMs = frameCount * VoiceCodec.FRAME_MS
        val tempFile = outputFile ?: return null
        outputFile = null

        if (durationMs <= 0L) {
            tempFile.delete()
            return null
        }

        val finalFile = VoiceMessageFile.renameWithDuration(tempFile, durationMs)
        return RecordedVoiceMessage(finalFile, durationMs)
    }

    /** Discards an in-progress recording without producing a file. */
    fun cancel() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        outputStream?.close()
        outputStream = null
        outputFile?.delete()
        outputFile = null
    }

    fun release() {
        codec.release()
    }
}

data class RecordedVoiceMessage(
    val file: File,
    val durationMs: Long
)
