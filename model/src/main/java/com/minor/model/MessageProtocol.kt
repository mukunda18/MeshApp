package com.minor.model

object MessageProtocol {
    const val MESSAGE_ID_LENGTH = 8
    const val TIMESTAMP_LENGTH = 8
    const val CONTENT_LEN_LENGTH = 4

    object messageId : Field<MessageId> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<MessageId> =
            ReadWithLength(MessageId(readI64(data, baseOffset)), MESSAGE_ID_LENGTH)

        override fun write(data: ByteArray, value: MessageId, baseOffset: Int): Int {
            writeI64(data, baseOffset, value.value)
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
