package com.meshapp.model

object VoicePacketProtocol {
    const val CALL_ID_LENGTH = 8
    const val SEQUENCE_NUMBER_LENGTH = 4
    const val TIMESTAMP_LENGTH = 8
    const val AUDIO_LEN_LENGTH = 2

    object callId : Field<MessageId> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<MessageId> =
            ReadWithLength(MessageId(readBytes(data, baseOffset, CALL_ID_LENGTH)), CALL_ID_LENGTH)

        override fun write(data: ByteArray, value: MessageId, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.bytes, CALL_ID_LENGTH)
            return CALL_ID_LENGTH
        }
    }

    object sequenceNumber : Field<Int> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU32(data, baseOffset).toInt(), SEQUENCE_NUMBER_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU32(data, baseOffset, value.toLong() and 0xFFFFFFFFL)
            return SEQUENCE_NUMBER_LENGTH
        }
    }

    object timestamp : Field<Timestamp> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Timestamp> =
            ReadWithLength(Timestamp(readI64(data, baseOffset)), TIMESTAMP_LENGTH)

        override fun write(data: ByteArray, value: Timestamp, baseOffset: Int): Int {
            writeI64(data, baseOffset, value.millis)
            return TIMESTAMP_LENGTH
        }
    }

    object encodedAudio : Field<ByteArray> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<ByteArray> {
            val len = readU16(data, baseOffset)
            return ReadWithLength(
                readBytes(data, baseOffset + AUDIO_LEN_LENGTH, len),
                AUDIO_LEN_LENGTH + len
            )
        }

        override fun write(data: ByteArray, value: ByteArray, baseOffset: Int): Int {
            require(value.size <= 0xFFFF) { "Encoded audio too large: ${value.size}" }
            writeU16(data, baseOffset, value.size)
            writeBytes(data, baseOffset + AUDIO_LEN_LENGTH, value, value.size)
            return AUDIO_LEN_LENGTH + value.size
        }
    }

    object voicePacket : Field<VoicePacket> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<VoicePacket> {
            var cursor = baseOffset
            val callId = VoicePacketProtocol.callId.read(data, cursor).also { cursor += it.bytesRead }.value
            val sequence = VoicePacketProtocol.sequenceNumber.read(data, cursor).also { cursor += it.bytesRead }.value
            val timestamp = VoicePacketProtocol.timestamp.read(data, cursor).also { cursor += it.bytesRead }.value
            val audio = VoicePacketProtocol.encodedAudio.read(data, cursor).also { cursor += it.bytesRead }.value
            return ReadWithLength(
                VoicePacket(callId, sequence, timestamp.millis, audio),
                cursor - baseOffset
            )
        }

        override fun write(data: ByteArray, value: VoicePacket, baseOffset: Int): Int {
            var cursor = baseOffset
            cursor += VoicePacketProtocol.callId.write(data, value.callId, cursor)
            cursor += VoicePacketProtocol.sequenceNumber.write(data, value.sequenceNumber, cursor)
            cursor += VoicePacketProtocol.timestamp.write(data, Timestamp(value.timestampMs), cursor)
            cursor += VoicePacketProtocol.encodedAudio.write(data, value.encodedAudio, cursor)
            return cursor - baseOffset
        }
    }
}
