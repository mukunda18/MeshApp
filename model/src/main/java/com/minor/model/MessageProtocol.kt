package com.minor.model

object MessageProtocol {
    const val MESSAGE_ID_OFFSET = 0
    const val MESSAGE_ID_LENGTH = 8

    const val TIMESTAMP_OFFSET = MESSAGE_ID_OFFSET + MESSAGE_ID_LENGTH
    const val TIMESTAMP_LENGTH = 8

    const val CONTENT_LEN_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_LENGTH
    const val CONTENT_LEN_LENGTH = 4
    const val CONTENT_OFFSET = CONTENT_LEN_OFFSET + CONTENT_LEN_LENGTH

    object messageId : Field<MessageId> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<MessageId> =
            ReadWithLength(MessageId(readI64(data, baseOffset + MESSAGE_ID_OFFSET)), MESSAGE_ID_LENGTH)

        override fun write(data: ByteArray, value: MessageId, baseOffset: Int): Int {
            writeI64(data, baseOffset + MESSAGE_ID_OFFSET, value.value)
            return MESSAGE_ID_LENGTH
        }
    }

    object timestamp : Field<Timestamp> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Timestamp> =
            ReadWithLength(Timestamp(readI64(data, baseOffset + TIMESTAMP_OFFSET)), TIMESTAMP_LENGTH)

        override fun write(data: ByteArray, value: Timestamp, baseOffset: Int): Int {
            writeI64(data, baseOffset + TIMESTAMP_OFFSET, value.millis)
            return TIMESTAMP_LENGTH
        }
    }

    object content : Field<String> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<String> {
            val len = readU32(data, baseOffset + CONTENT_LEN_OFFSET).toInt()
            return ReadWithLength(
                readString(data, baseOffset + CONTENT_OFFSET, len),
                CONTENT_LEN_LENGTH + len
            )
        }

        override fun write(data: ByteArray, value: String, baseOffset: Int): Int {
            val bytes = value.encodeToByteArray()
            writeU32(data, baseOffset + CONTENT_LEN_OFFSET, bytes.size.toLong())
            writeBytes(data, baseOffset + CONTENT_OFFSET, bytes, bytes.size)
            return CONTENT_LEN_LENGTH + bytes.size
        }
    }
}
