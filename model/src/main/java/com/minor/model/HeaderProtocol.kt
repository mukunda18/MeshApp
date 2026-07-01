package com.minor.model

object HeaderProtocol {

    private const val MAGIC_OFFSET = 0
    private const val MAGIC_LENGTH = 2

    private const val VERSION_OFFSET = MAGIC_OFFSET + MAGIC_LENGTH
    private const val VERSION_LENGTH = 1

    private const val TYPE_OFFSET = VERSION_OFFSET + VERSION_LENGTH
    private const val TYPE_LENGTH = 1

    private const val FLAGS_OFFSET = TYPE_OFFSET + TYPE_LENGTH
    private const val FLAGS_LENGTH = 1

    private const val HOPCOUNT_OFFSET = FLAGS_OFFSET + FLAGS_LENGTH
    private const val HOPCOUNT_LENGTH = 1

    private const val TTL_OFFSET = HOPCOUNT_OFFSET + HOPCOUNT_LENGTH
    private const val TTL_LENGTH = 1

    private const val RESERVED_OFFSET = TTL_OFFSET + TTL_LENGTH
    private const val RESERVED_LENGTH = 1

    private const val SOURCE_NODE_ID_OFFSET = RESERVED_OFFSET + RESERVED_LENGTH
    private const val SOURCE_NODE_ID_LENGTH = 32

    private const val DEST_NODE_ID_OFFSET = SOURCE_NODE_ID_OFFSET + SOURCE_NODE_ID_LENGTH
    private const val DEST_NODE_ID_LENGTH = 32

    private const val ID_OFFSET = DEST_NODE_ID_OFFSET + DEST_NODE_ID_LENGTH
    private const val ID_LENGTH = 8

    private const val ORIGIN_TS_OFFSET = ID_OFFSET + ID_LENGTH
    private const val ORIGIN_TS_LENGTH = 8

    private const val PAYLOAD_LEN_OFFSET = ORIGIN_TS_OFFSET + ORIGIN_TS_LENGTH
    private const val PAYLOAD_LEN_LENGTH = 2
    const val MAX_PAYLOAD = 0xFFFF

    const val HEADER_SIZE = PAYLOAD_LEN_OFFSET + PAYLOAD_LEN_LENGTH

    object Magic : Field<Int> {
        const val EXPECTED = 0x4D45
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU16(data, baseOffset + MAGIC_OFFSET), MAGIC_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU16(data, baseOffset + MAGIC_OFFSET, value)
            return MAGIC_LENGTH
        }
    }

    object Version : Field<Int> {
        const val SUPPORTED_VERSION = 1
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU8(data, baseOffset + VERSION_OFFSET), VERSION_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU8(data, baseOffset + VERSION_OFFSET, value)
            return VERSION_LENGTH
        }
    }

    object Type : Field<Int> {
        const val HELLO = 0x01
        const val MESSAGE = 0x02
        const val RREQ = 0x03
        const val RREP = 0x04
        const val ACK = 0x05
        const val RERR = 0x06
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU8(data, baseOffset + TYPE_OFFSET), TYPE_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU8(data, baseOffset + TYPE_OFFSET, value)
            return TYPE_LENGTH
        }
    }

    object Flags : Field<Int> {
        const val BROADCAST = 0x01
        const val ENCRYPTED = 0x02
        const val ACK_REQUESTED = 0x04
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU8(data, baseOffset + FLAGS_OFFSET), FLAGS_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU8(data, baseOffset + FLAGS_OFFSET, value)
            return FLAGS_LENGTH
        }
    }

    object Hopcount : Field<Int> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU8(data, baseOffset + HOPCOUNT_OFFSET), HOPCOUNT_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU8(data, baseOffset + HOPCOUNT_OFFSET, value)
            return HOPCOUNT_LENGTH
        }
    }

    object TTL : Field<Int> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU8(data, baseOffset + TTL_OFFSET), TTL_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU8(data, baseOffset + TTL_OFFSET, value)
            return TTL_LENGTH
        }
    }

    object Reserved : Field<Int> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU8(data, baseOffset + RESERVED_OFFSET), RESERVED_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU8(data, baseOffset + RESERVED_OFFSET, value)
            return RESERVED_LENGTH
        }
    }

    object SourceNodeId : Field<NodeId> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<NodeId> =
            ReadWithLength(
                NodeId(readBytes(data, baseOffset + SOURCE_NODE_ID_OFFSET, SOURCE_NODE_ID_LENGTH)),
                SOURCE_NODE_ID_LENGTH
            )

        override fun write(data: ByteArray, value: NodeId, baseOffset: Int): Int {
            writeBytes(data, baseOffset + SOURCE_NODE_ID_OFFSET, value.bytes, SOURCE_NODE_ID_LENGTH)
            return SOURCE_NODE_ID_LENGTH
        }
    }

    object DestNodeId : Field<NodeId> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<NodeId> =
            ReadWithLength(
                NodeId(readBytes(data, baseOffset + DEST_NODE_ID_OFFSET, DEST_NODE_ID_LENGTH)),
                DEST_NODE_ID_LENGTH
            )

        override fun write(data: ByteArray, value: NodeId, baseOffset: Int): Int {
            writeBytes(data, baseOffset + DEST_NODE_ID_OFFSET, value.bytes, DEST_NODE_ID_LENGTH)
            return DEST_NODE_ID_LENGTH
        }
    }

    object id : Field<MessageId> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<MessageId> =
            ReadWithLength(MessageId(readI64(data, baseOffset + ID_OFFSET)), ID_LENGTH)

        override fun write(data: ByteArray, value: MessageId, baseOffset: Int): Int {
            writeI64(data, baseOffset + ID_OFFSET, value.value)
            return ID_LENGTH
        }
    }

    object OriginTimestamp : Field<Timestamp> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Timestamp> =
            ReadWithLength(Timestamp(readI64(data, baseOffset + ORIGIN_TS_OFFSET)), ORIGIN_TS_LENGTH)

        override fun write(data: ByteArray, value: Timestamp, baseOffset: Int): Int {
            writeI64(data, baseOffset + ORIGIN_TS_OFFSET, value.millis)
            return ORIGIN_TS_LENGTH
        }
    }

    object PayloadLength : Field<Int> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<Int> =
            ReadWithLength(readU16(data, baseOffset + PAYLOAD_LEN_OFFSET), PAYLOAD_LEN_LENGTH)

        override fun write(data: ByteArray, value: Int, baseOffset: Int): Int {
            writeU16(data, baseOffset + PAYLOAD_LEN_OFFSET, value)
            return PAYLOAD_LEN_LENGTH
        }
    }
}
