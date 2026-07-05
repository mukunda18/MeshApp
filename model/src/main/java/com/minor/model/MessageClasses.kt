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
fun randomMessageId(): MessageId = MessageId(java.util.UUID.randomUUID().mostSignificantBits)

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
    val timestamp: Timestamp = Timestamp(0),
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

data class SecureEnvelope(
    val envVersion: Int,
    val senderNodeId: NodeId,
    val encSymKey: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val signature: Signature
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SecureEnvelope
        if (envVersion != other.envVersion) return false
        if (senderNodeId != other.senderNodeId) return false
        if (!encSymKey.contentEquals(other.encSymKey)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (signature != other.signature) return false
        return true
    }

    override fun hashCode(): Int {
        var result = envVersion
        result = 31 * result + senderNodeId.hashCode()
        result = 31 * result + encSymKey.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + signature.hashCode()
        return result
    }
}

data class InnerPlaintextBlock(
    val messageId: MessageId,
    val timestamp: Timestamp,
    val content: String
)

data class DecodedContent(
    val senderNodeId: NodeId,
    val messageId: MessageId,
    val timestamp: Timestamp,
    val content: String
)

interface MessageSecurityCodec {
    fun encode(plaintext: String, recipientNodeID: NodeId, messageID: MessageId): ByteArray
    fun decode(envelopeBytes: ByteArray): DecodedContent
}

sealed interface Payload {
    data class Hello(
        val name: String,
        val publicKey: PublicKey,
        val routeEntries: List<RouteEntry>
    ) : Payload

    data class Message(
        val envelope: SecureEnvelope
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
