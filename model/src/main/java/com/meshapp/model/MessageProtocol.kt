package com.meshapp.model

object MessageProtocol {
    const val MESSAGE_ID_LENGTH = 8
    const val TIMESTAMP_LENGTH = 8
    const val CONTENT_LEN_LENGTH = 4

    // --- Secure Envelope Fields (5.2.1) ---
    const val ENV_VERSION_LENGTH = 1
    const val SENDER_NODE_ID_LENGTH = 32
    const val ENC_SYM_KEY_LENGTH = 91  // P-256 DER-encoded ephemeral public key
    const val NONCE_LENGTH = 12
    const val CIPHER_LEN_LENGTH = 4
    const val SIGNATURE_LENGTH = 64

    object envVersion : Field<Int> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU8(data, baseOffset), ENV_VERSION_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU8(data, baseOffset, value)
            return ENV_VERSION_LENGTH
        }
    }

    object senderNodeId : Field<NodeId> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<NodeId> =
            ReadWithLength(NodeId(readBytes(data, baseOffset, SENDER_NODE_ID_LENGTH)), SENDER_NODE_ID_LENGTH)

        override fun write(data: ByteArray, value: NodeId, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.bytes, SENDER_NODE_ID_LENGTH)
            return SENDER_NODE_ID_LENGTH
        }
    }

    object encSymKey : Field<ByteArray> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<ByteArray> =
            ReadWithLength(readBytes(data, baseOffset, ENC_SYM_KEY_LENGTH), ENC_SYM_KEY_LENGTH)

        override fun write(data: ByteArray, value: ByteArray, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value, ENC_SYM_KEY_LENGTH)
            return ENC_SYM_KEY_LENGTH
        }
    }

    object nonce : Field<ByteArray> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<ByteArray> =
            ReadWithLength(readBytes(data, baseOffset, NONCE_LENGTH), NONCE_LENGTH)

        override fun write(data: ByteArray, value: ByteArray, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value, NONCE_LENGTH)
            return NONCE_LENGTH
        }
    }

    object ciphertext : Field<ByteArray> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<ByteArray> {
            val len = readU32(data, baseOffset).toInt()
            return ReadWithLength(
                readBytes(data, baseOffset + CIPHER_LEN_LENGTH, len),
                CIPHER_LEN_LENGTH + len
            )
        }

        override fun write(data: ByteArray, value: ByteArray, baseOffset: Int): Int {
            writeU32(data, baseOffset, value.size.toLong())
            writeBytes(data, baseOffset + CIPHER_LEN_LENGTH, value, value.size)
            return CIPHER_LEN_LENGTH + value.size
        }
    }

    object signature : Field<Signature> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Signature> =
            ReadWithLength(Signature(readBytes(data, baseOffset, SIGNATURE_LENGTH)), SIGNATURE_LENGTH)

        override fun write(data: ByteArray, value: Signature, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.bytes, SIGNATURE_LENGTH)
            return SIGNATURE_LENGTH
        }
    }

    // --- Inner Plaintext Block Fields (5.2.2) ---
    // These live inside the ciphertext and are used after decryption.

    object messageId : Field<MessageId> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<MessageId> =
            ReadWithLength(MessageId(readBytes(data, baseOffset, MESSAGE_ID_LENGTH)), MESSAGE_ID_LENGTH)

        override fun write(data: ByteArray, value: MessageId, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.bytes, MESSAGE_ID_LENGTH)
            return MESSAGE_ID_LENGTH
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

    object content : Field<String> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<String> {
            val len = readU32(data, baseOffset).toInt()
            return ReadWithLength(
                readString(data, baseOffset + CONTENT_LEN_LENGTH, len),
                CONTENT_LEN_LENGTH + len
            )
        }

        override fun write(data: ByteArray, value: String, baseOffset: Int): Int {
            val bytes = value.encodeToByteArray()
            writeU32(data, baseOffset, bytes.size.toLong())
            writeBytes(data, baseOffset + CONTENT_LEN_LENGTH, bytes, bytes.size)
            return CONTENT_LEN_LENGTH + bytes.size
        }
    }
}
