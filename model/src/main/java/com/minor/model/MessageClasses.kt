package com.minor.model

import java.net.InetSocketAddress

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

@JvmInline
value class PublicKey(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) { "PublicKey must be exactly 32 bytes, got ${bytes.size}" }
    }
    override fun toString(): String = bytes.joinToString("") { "%02x".format(it) }
}

@JvmInline
value class Signature(val bytes: ByteArray) {
    init {
        require(bytes.size == 64) { "Signature must be exactly 64 bytes, got ${bytes.size}" }
    }
    override fun toString(): String = bytes.joinToString("") { "%02x".format(it) }
}

@JvmInline
value class Timestamp(val millis: Long) {
    override fun toString(): String = millis.toString()
}

interface Field<T> {
    fun read(data: ByteArray, baseOffset: Int = 0): ReadWithLength<T>
    fun write(data: ByteArray, value: T, baseOffset: Int = 0): Int
}

data class ReadWithLength<T>(
    val value: T,
    val bytesRead: Int
)

data class RouteEntry(
    val nodeId: NodeId,
    val hopcount: Int,
    val publicKey: PublicKey,
    val name: String
)

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
    val originTimestamp: Timestamp,
    val payloadLength: Int
)

sealed interface Payload {
    data class Hello(
        val name: String,
        val publicKey: PublicKey,
        val routeEntries: List<RouteEntry>
    ) : Payload

    data class Message(
        val messageId: MessageId,
        val timestamp: Timestamp,
        val content: String
    ) : Payload

    data class Ack(
        val status: Int,
        val signature: Signature
    ) : Payload

    data class RREQ(
        val name: String,
        val publicKey: PublicKey
    ) : Payload

    data class RREP(
        val name: String,
        val publicKey: PublicKey
    ) : Payload

    data class RERR(
        val destinations: List<NodeId>
    ) : Payload
}

data class Packet(
    val header: Header,
    val payload: ByteArray
)

data class Envelope(
    val packet: Packet,
    val remoteAddress: InetSocketAddress
)
