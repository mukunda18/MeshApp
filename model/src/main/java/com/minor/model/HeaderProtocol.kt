package com.minor.model

// ---------------------------------------------------------------------------
// Field interface
// ---------------------------------------------------------------------------

interface Field<T> {
    val offset: Int
    val length: Int
    fun read(data: ByteArray): T
    fun write(data: ByteArray, value: T)
}

// ---------------------------------------------------------------------------
// Primitive read/write helpers (big-endian)
// ---------------------------------------------------------------------------

internal fun readU8(data: ByteArray, offset: Int): Int =
    data[offset].toInt() and 0xFF

internal fun writeU8(data: ByteArray, offset: Int, value: Int) {
    data[offset] = (value and 0xFF).toByte()
}

internal fun readU16(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 8) or
            (data[offset + 1].toInt() and 0xFF)

internal fun writeU16(data: ByteArray, offset: Int, value: Int) {
    data[offset]     = ((value shr 8) and 0xFF).toByte()
    data[offset + 1] = (value and 0xFF).toByte()
}

internal fun readI64(data: ByteArray, offset: Int): Long {
    var result = 0L
    for (i in 0..7) result = (result shl 8) or (data[offset + i].toLong() and 0xFF)
    return result
}

internal fun writeI64(data: ByteArray, offset: Int, value: Long) {
    for (i in 0..7) data[offset + i] = ((value shr (56 - i * 8)) and 0xFF).toByte()
}

internal fun readBytes(data: ByteArray, offset: Int, length: Int): ByteArray =
    data.copyOfRange(offset, offset + length)

internal fun writeBytes(data: ByteArray, offset: Int, value: ByteArray, length: Int) {
    require(value.size == length) { "Expected $length bytes, got ${value.size}" }
    value.copyInto(data, offset)
}

// ---------------------------------------------------------------------------
// HeaderProtocol
//
// Layout constants are plain const vals so they can be used in
// constant expressions. Each field object references them for its
// offset/length but adds the Field<T> read/write logic on top.
// ---------------------------------------------------------------------------

object HeaderProtocol {

    private const val MAGIC_OFFSET          = 0
    private const val MAGIC_LENGTH          = 2

    private const val VERSION_OFFSET        = MAGIC_OFFSET + MAGIC_LENGTH
    private const val VERSION_LENGTH        = 1

    private const val TYPE_OFFSET           = VERSION_OFFSET + VERSION_LENGTH
    private const val TYPE_LENGTH           = 1

    private const val FLAGS_OFFSET          = TYPE_OFFSET + TYPE_LENGTH
    private const val FLAGS_LENGTH          = 1

    private const val HOPCOUNT_OFFSET       = FLAGS_OFFSET + FLAGS_LENGTH
    private const val HOPCOUNT_LENGTH       = 1

    private const val TTL_OFFSET            = HOPCOUNT_OFFSET + HOPCOUNT_LENGTH
    private const val TTL_LENGTH            = 1

    private const val RESERVED_OFFSET       = TTL_OFFSET + TTL_LENGTH
    private const val RESERVED_LENGTH       = 1

    private const val SOURCE_NODE_ID_OFFSET = RESERVED_OFFSET + RESERVED_LENGTH
    private const val SOURCE_NODE_ID_LENGTH = 32

    private const val DEST_NODE_ID_OFFSET   = SOURCE_NODE_ID_OFFSET + SOURCE_NODE_ID_LENGTH
    private const val DEST_NODE_ID_LENGTH   = 32

    private const val ID_OFFSET             = DEST_NODE_ID_OFFSET + DEST_NODE_ID_LENGTH
    private const val ID_LENGTH             = 8

    private const val ORIGIN_TS_OFFSET      = ID_OFFSET + ID_LENGTH
    private const val ORIGIN_TS_LENGTH      = 8

    private const val PAYLOAD_LEN_OFFSET    = ORIGIN_TS_OFFSET + ORIGIN_TS_LENGTH
    private const val PAYLOAD_LEN_LENGTH    = 2

    const val HEADER_SIZE = PAYLOAD_LEN_OFFSET + PAYLOAD_LEN_LENGTH

    // --- Field objects ------------------------------------------------------

    object magic : Field<Int> {
        override val offset = MAGIC_OFFSET
        override val length = MAGIC_LENGTH
        const val EXPECTED  = 0x4D45
        override fun read(data: ByteArray): Int          = readU16(data, offset)
        override fun write(data: ByteArray, value: Int)  = writeU16(data, offset, value)
    }

    object version : Field<Int> {
        override val offset = VERSION_OFFSET
        override val length = VERSION_LENGTH
        override fun read(data: ByteArray): Int          = readU8(data, offset)
        override fun write(data: ByteArray, value: Int)  = writeU8(data, offset, value)
    }

    object type : Field<Int> {
        override val offset = TYPE_OFFSET
        override val length = TYPE_LENGTH
        override fun read(data: ByteArray): Int          = readU8(data, offset)
        override fun write(data: ByteArray, value: Int)  = writeU8(data, offset, value)
    }

    object flags : Field<Int> {
        override val offset = FLAGS_OFFSET
        override val length = FLAGS_LENGTH
        const val BROADCAST = 0x01
        const val ENCRYPTED = 0x02
        const val ACK_REQUESTED = 0x04
        override fun read(data: ByteArray): Int          = readU8(data, offset)
        override fun write(data: ByteArray, value: Int)  = writeU8(data, offset, value)
        fun hasFlag(value: Int, flag: Int) = (value and flag) != 0
    }

    object hopcount : Field<Int> {
        override val offset = HOPCOUNT_OFFSET
        override val length = HOPCOUNT_LENGTH
        override fun read(data: ByteArray): Int          = readU8(data, offset)
        override fun write(data: ByteArray, value: Int)  = writeU8(data, offset, value)
    }

    object ttl : Field<Int> {
        override val offset = TTL_OFFSET
        override val length = TTL_LENGTH
        override fun read(data: ByteArray): Int          = readU8(data, offset)
        override fun write(data: ByteArray, value: Int)  = writeU8(data, offset, value)
    }

    object reserved : Field<Int> {
        override val offset = RESERVED_OFFSET
        override val length = RESERVED_LENGTH
        override fun read(data: ByteArray): Int          = readU8(data, offset)
        override fun write(data: ByteArray, value: Int)  = writeU8(data, offset, value)
    }

    object sourceNodeId : Field<NodeId> {
        override val offset = SOURCE_NODE_ID_OFFSET
        override val length = SOURCE_NODE_ID_LENGTH
        override fun read(data: ByteArray): NodeId              = NodeId(readBytes(data, offset, length))
        override fun write(data: ByteArray, value: NodeId)      = writeBytes(data, offset, value.bytes, length)
    }

    object destNodeId : Field<NodeId> {
        override val offset = DEST_NODE_ID_OFFSET
        override val length = DEST_NODE_ID_LENGTH
        override fun read(data: ByteArray): NodeId              = NodeId(readBytes(data, offset, length))
        override fun write(data: ByteArray, value: NodeId)      = writeBytes(data, offset, value.bytes, length)
    }

    object id : Field<MessageId> {
        override val offset = ID_OFFSET
        override val length = ID_LENGTH
        override fun read(data: ByteArray): MessageId           = MessageId(readI64(data, offset))
        override fun write(data: ByteArray, value: MessageId)   = writeI64(data, offset, value.value)
    }

    object originTimestamp : Field<Long> {
        override val offset = ORIGIN_TS_OFFSET
        override val length = ORIGIN_TS_LENGTH
        override fun read(data: ByteArray): Long                = readI64(data, offset)
        override fun write(data: ByteArray, value: Long)        = writeI64(data, offset, value)
    }

    object payloadLength : Field<Int> {
        override val offset = PAYLOAD_LEN_OFFSET
        override val length = PAYLOAD_LEN_LENGTH
        override fun read(data: ByteArray): Int                 = readU16(data, offset)
        override fun write(data: ByteArray, value: Int)         = writeU16(data, offset, value)
    }
}