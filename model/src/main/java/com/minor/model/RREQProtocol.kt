package com.minor.model

object RREQProtocol {
    const val NAME_LEN_OFFSET = 0
    const val NAME_LEN_LENGTH = 1
    const val NAME_OFFSET = 1
    
    const val PUBLIC_KEY_LENGTH = 32

    object name : Field<String> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<String> {
            val len = readU8(data, baseOffset + NAME_LEN_OFFSET)
            return ReadWithLength(
                readString(data, baseOffset + NAME_OFFSET, len),
                NAME_LEN_LENGTH + len
            )
        }

        override fun write(data: ByteArray, value: String, baseOffset: Int): Int {
            val bytes = value.encodeToByteArray()
            writeU8(data, baseOffset + NAME_LEN_OFFSET, bytes.size)
            writeBytes(data, baseOffset + NAME_OFFSET, bytes, bytes.size)
            return NAME_LEN_LENGTH + bytes.size
        }
    }

    object publicKey : Field<PublicKey> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<PublicKey> {
            return ReadWithLength(PublicKey(readBytes(data, baseOffset, PUBLIC_KEY_LENGTH)), PUBLIC_KEY_LENGTH)
        }

        override fun write(data: ByteArray, value: PublicKey, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.bytes, PUBLIC_KEY_LENGTH)
            return PUBLIC_KEY_LENGTH
        }
    }
}
