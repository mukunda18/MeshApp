package com.minor.model

object RouteProtocol {
    private const val NODE_ID_OFFSET = 0
    private const val NODE_ID_LENGTH = 32

    private const val HOPCOUNT_OFFSET = NODE_ID_OFFSET + NODE_ID_LENGTH
    private const val HOPCOUNT_LENGTH = 1

    private const val PUBLIC_KEY_OFFSET = HOPCOUNT_OFFSET + HOPCOUNT_LENGTH
    private const val PUBLIC_KEY_LENGTH = 32

    private const val NAME_LEN_OFFSET = PUBLIC_KEY_OFFSET + PUBLIC_KEY_LENGTH
    private const val NAME_LEN_LENGTH = 1

    private const val NAME_OFFSET = NAME_LEN_OFFSET + NAME_LEN_LENGTH

    object nodeId : Field<NodeId> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<NodeId> =
            ReadWithLength(
                NodeId(readBytes(data, baseOffset + NODE_ID_OFFSET, NODE_ID_LENGTH)),
                NODE_ID_LENGTH
            )

        override fun write(data: ByteArray, value: NodeId, baseOffset: Int): Int {
            writeBytes(data, baseOffset + NODE_ID_OFFSET, value.bytes, NODE_ID_LENGTH)
            return NODE_ID_LENGTH
        }
    }

    object hopcount : Field<Int> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU8(data, baseOffset + HOPCOUNT_OFFSET), HOPCOUNT_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU8(data, baseOffset + HOPCOUNT_OFFSET, value)
            return HOPCOUNT_LENGTH
        }
    }

    object publicKey : Field<PublicKey> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<PublicKey> =
            ReadWithLength(
                PublicKey(readBytes(data, baseOffset + PUBLIC_KEY_OFFSET, PUBLIC_KEY_LENGTH)),
                PUBLIC_KEY_LENGTH
            )

        override fun write(data: ByteArray, value: PublicKey, baseOffset: Int): Int {
            writeBytes(data, baseOffset + PUBLIC_KEY_OFFSET, value.bytes, PUBLIC_KEY_LENGTH)
            return PUBLIC_KEY_LENGTH
        }
    }

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
}
