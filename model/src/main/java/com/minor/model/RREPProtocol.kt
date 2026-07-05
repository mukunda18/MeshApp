package com.minor.model

object RREPProtocol {
    const val PUBLIC_KEY_LENGTH = 91  // P-256 DER-encoded public key
    const val NAME_LEN_LENGTH = 1

    object Name : Field<String> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<String> {
            val len = readU8(data, baseOffset)
            return ReadWithLength(
                readString(data, baseOffset + 1, len),
                NAME_LEN_LENGTH + len,
            )
        }

        override fun write(data: ByteArray, value: String, baseOffset: Int): Int {
            val bytes = value.encodeToByteArray()
            writeU8(data, baseOffset, bytes.size)
            writeBytes(data, baseOffset + 1, bytes, bytes.size)
            return NAME_LEN_LENGTH + bytes.size
        }
    }

    object PublicKey : Field<com.minor.model.PublicKey> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<com.minor.model.PublicKey> =
            ReadWithLength(PublicKey(readBytes(data, baseOffset, PUBLIC_KEY_LENGTH)), PUBLIC_KEY_LENGTH)

        override fun write(data: ByteArray, value: com.minor.model.PublicKey, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.bytes, PUBLIC_KEY_LENGTH)
            return PUBLIC_KEY_LENGTH
        }
    }
}
