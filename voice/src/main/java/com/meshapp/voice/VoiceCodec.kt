package com.meshapp.voice

/**
 * Lightweight audio codec for mesh voice calls.
 *
 * Uses 16 kHz mono, 20 ms frames (320 samples). This codec applies simple 
 * mu-law compression to raw 16-bit PCM.
 * mu-law reduces each sample from 16 bits to 8 bits (50% compression) with
 * quality that is perfectly acceptable for voice, and it is pure Kotlin with
 * no native dependencies.
 */
class VoiceCodec {
    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_MS = 20
        const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_MS / 1000 // 320
        const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2 // 16-bit PCM
        const val COMPRESSED_FRAME_SIZE = SAMPLES_PER_FRAME // 8-bit mu-law

        // Standard G.711 mu-law constants.
        private const val MULAW_BIAS = 0x84
        private const val MULAW_CLIP = 32635

        private val muLawEncodeTable = ByteArray(65536) { i ->
            linearToMuLaw(i - 32768).toByte()
        }
        private val muLawDecodeTable = ShortArray(256) { muLawToLinear(it).toShort() }

        private fun linearToMuLaw(sample: Int): Int {
            var pcm = sample
            val sign = if (pcm < 0) 0x80 else 0x00
            if (pcm < 0) {
                pcm = -pcm
            }
            if (pcm > MULAW_CLIP) {
                pcm = MULAW_CLIP
            }
            pcm += MULAW_BIAS

            var exponent = 7
            var expMask = 0x4000
            while (exponent > 0 && (pcm and expMask) == 0) {
                exponent--
                expMask = expMask shr 1
            }
            val mantissa = (pcm shr (exponent + 3)) and 0x0F
            return (sign or (exponent shl 4) or mantissa).inv() and 0xFF
        }

        private fun muLawToLinear(muLaw: Int): Int {
            val value = muLaw.inv() and 0xFF
            val sign = value and 0x80
            val exponent = (value shr 4) and 0x07
            val mantissa = value and 0x0F
            var sample = ((mantissa shl 3) + MULAW_BIAS) shl exponent
            sample -= MULAW_BIAS
            return if (sign != 0) -sample else sample
        }
    }

    fun encode(pcm16: ByteArray): ByteArray {
        require(pcm16.size == BYTES_PER_FRAME) {
            "Expected $BYTES_PER_FRAME bytes of PCM, got ${pcm16.size}"
        }
        val out = ByteArray(COMPRESSED_FRAME_SIZE)
        var i = 0
        var o = 0
        while (i < pcm16.size) {
            val low = pcm16[i].toInt() and 0xFF
            val high = (pcm16[i + 1].toInt() and 0xFF) shl 8
            var sample = high or low
            if (sample and 0x8000 != 0) sample -= 0x10000
            out[o++] = muLawEncodeTable[sample + 32768]
            i += 2
        }
        return out
    }

    fun decode(compressed: ByteArray): ByteArray {
        require(compressed.size == COMPRESSED_FRAME_SIZE) {
            "Expected $COMPRESSED_FRAME_SIZE bytes of mu-law, got ${compressed.size}"
        }
        val out = ByteArray(BYTES_PER_FRAME)
        var i = 0
        var o = 0
        while (i < compressed.size) {
            val sample = muLawDecodeTable[compressed[i].toInt() and 0xFF].toInt()
            out[o++] = (sample and 0xFF).toByte()
            out[o++] = ((sample shr 8) and 0xFF).toByte()
            i++
        }
        return out
    }

    fun release() {
        // Nothing to release for pure-Kotlin codec.
    }
}
