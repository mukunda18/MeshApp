package com.meshapp.voicemessage

import android.content.Context
import java.io.File

/**
 * Voice messages travel through the existing FileTransferService pipeline
 * unmodified: they are ordinary files as far as FileTransferMetadata and
 * FILE_CHUNK are concerned. To let the UI layer recognize and render them as
 * voice-message bubbles instead of generic file attachments, this object
 * defines a filename convention rather than a wire-protocol change:
 *
 *   voice_note_<epochMillis>_<durationMs>ms.mulaw
 *
 * This keeps :model, :filetransfer, and the FILE_SIGNAL/FILE_CHUNK protocol
 * completely untouched.
 */
object VoiceMessageFile {
    private const val PREFIX = "voice_note_"
    private const val SUFFIX = "ms.mulaw"
    private val NAME_PATTERN = Regex("""^voice_note_(\d+)_(\d+)ms\.mulaw$""")

    fun outgoingDir(context: Context): File =
        File(context.cacheDir, "voice_messages/outgoing").apply { if (!exists()) mkdirs() }

    /** Temp file used while a recording is in progress; duration is unknown until stop(). */
    fun newOutgoingTempFile(context: Context): File =
        File(outgoingDir(context), "voice_note_${System.currentTimeMillis()}_recording.mulaw.tmp")

    /** Renames a finished recording to the canonical `voice_note_<ts>_<durMs>ms.mulaw` form. */
    fun renameWithDuration(tempFile: File, durationMs: Long): File {
        val finalFile = File(tempFile.parentFile, "$PREFIX${System.currentTimeMillis()}_$durationMs$SUFFIX")
        tempFile.renameTo(finalFile)
        return finalFile
    }

    fun isVoiceMessage(filename: String): Boolean = NAME_PATTERN.matches(filename)

    fun durationMsOrNull(filename: String): Long? =
        NAME_PATTERN.matchEntire(filename)?.groupValues?.get(2)?.toLongOrNull()
}
