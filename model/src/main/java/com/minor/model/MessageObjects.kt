package com.minor.model

@JvmInline
value class NodeId(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) { "NodeId must be exactly 32 bytes, got ${bytes.size}" }
    }

    override fun toString(): String = bytes.joinToString("") { "%02x".format(it) }
}

@JvmInline
value class MessageId(val value: Long) {
    override fun toString(): String = "%016x".format(value)
}

data class Header(
    val magic: Int,
    val version: Int,
    val type: Int,
    val flags: Int,
    val hopcount: Int,
    val ttl: Int,
    val reserved: Int,
    val sourceNodeId: NodeId,
    val destNodeId: NodeId,
    val id: MessageId,
    val originTimestamp: Long,
    val payloadLength: Int
)

data class Packet(
    val header: Header,
    val payload: ByteArray
)