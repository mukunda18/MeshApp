package com.meshapp.model

object AckProtocol {
    const val STATUS_LENGTH = 1
    const val SIGNATURE_LENGTH = 64

    object status : Field<Int> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU8(data, baseOffset), STATUS_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU8(data, baseOffset, value)
            return STATUS_LENGTH
        }
    }

    object signature : Field<Signature> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Signature> =
            ReadWithLength(
                Signature(readBytes(data, baseOffset, SIGNATURE_LENGTH)),
                SIGNATURE_LENGTH
            )

        override fun write(data: ByteArray, value: Signature, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.bytes, SIGNATURE_LENGTH)
            return SIGNATURE_LENGTH
        }
    }
}
