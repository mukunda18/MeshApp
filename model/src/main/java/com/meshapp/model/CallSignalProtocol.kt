package com.meshapp.model

object CallSignalProtocol {
    const val CALL_ID_LENGTH = 8
    const val SIGNAL_TYPE_LENGTH = 1
    const val PAYLOAD_LEN_LENGTH = 4
    const val PUBLIC_KEY_LENGTH = 91 // Fixed P-256 DER public key length

    object callSignal : Field<CallSignal> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<CallSignal> {
            var cursor = baseOffset
            val callId = MessageProtocol.messageId.read(data, cursor).also { cursor += it.bytesRead }.value
            val type = readU8(data, cursor).also { cursor += SIGNAL_TYPE_LENGTH }
            val len = readU32(data, cursor).toInt().also { cursor += PAYLOAD_LEN_LENGTH }
            val payload = readBytes(data, cursor, len).also { cursor += len }
            return ReadWithLength(CallSignal(callId, type, payload), cursor - baseOffset)
        }

        override fun write(data: ByteArray, value: CallSignal, baseOffset: Int): Int {
            var cursor = baseOffset
            cursor += MessageProtocol.messageId.write(data, value.callId, cursor)
            writeU8(data, cursor, value.type).also { cursor += SIGNAL_TYPE_LENGTH }
            writeU32(data, cursor, value.payload.size.toLong()).also { cursor += PAYLOAD_LEN_LENGTH }
            writeBytes(data, cursor, value.payload, value.payload.size).also { cursor += value.payload.size }
            return cursor - baseOffset
        }
    }

    object callOffer : Field<CallOffer> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<CallOffer> {
            val pubKey = readBytes(data, baseOffset, PUBLIC_KEY_LENGTH)
            return ReadWithLength(CallOffer(pubKey), PUBLIC_KEY_LENGTH)
        }

        override fun write(data: ByteArray, value: CallOffer, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.ephemeralPublicKey, PUBLIC_KEY_LENGTH)
            return PUBLIC_KEY_LENGTH
        }
    }

    object callAccept : Field<CallAccept> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<CallAccept> {
            val pubKey = readBytes(data, baseOffset, PUBLIC_KEY_LENGTH)
            return ReadWithLength(CallAccept(pubKey), PUBLIC_KEY_LENGTH)
        }

        override fun write(data: ByteArray, value: CallAccept, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.ephemeralPublicKey, PUBLIC_KEY_LENGTH)
            return PUBLIC_KEY_LENGTH
        }
    }
}
