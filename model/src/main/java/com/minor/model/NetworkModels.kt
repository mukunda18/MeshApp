package com.minor.model

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class Datagram(val address: String,val port: Int, val data: ByteArray)

enum class MessageType(val value: Byte) {
    HELLO(0x1),
    UNKNOWN(0xF);

    companion object {
        fun from(type: Byte): MessageType = entries.firstOrNull { it.value == type } ?: UNKNOWN
    }
}

sealed interface NetworkMessage{
    val address: String
    val port: Int
    fun toByteArray(): ByteArray

    data class HelloMessage(
        override val address: String, override  val port: Int, val name: String, val nodeID: String) :
        NetworkMessage{
            override fun toByteArray(): ByteArray {
                val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
                val nodeIDBytes = nodeID.toByteArray(StandardCharsets.UTF_8)
                return ByteBuffer.allocate(1 + Integer.BYTES + nameBytes.size + Integer.BYTES + nodeIDBytes.size)
                    .put(MessageType.HELLO.value)
                    .putInt(nameBytes.size)
                    .put(nameBytes)
                    .putInt(nodeIDBytes.size)
                    .put(nodeIDBytes)
                    .array()
            }
    }

    companion object  {
        fun fromByteArray(datagram: Datagram): NetworkMessage {
            val buffer = ByteBuffer.wrap(datagram.data)
            val typeVal = buffer.get()
            return when (val type = MessageType.from(typeVal)) {
                MessageType.HELLO -> {
                    val nameLength = buffer.getInt()
                    val nameBytes = ByteArray(nameLength)
                    buffer.get(nameBytes)
                    val name = String(nameBytes, StandardCharsets.UTF_8)
                    val nodeIDLength = buffer.getInt()
                    val nodeIDBytes = ByteArray(nodeIDLength)
                    buffer.get(nodeIDBytes)
                    val nodeID = String(nodeIDBytes, StandardCharsets.UTF_8)
                    HelloMessage(datagram.address, datagram.port, name, nodeID)
                }
                else -> {throw IllegalArgumentException("Unsupported message type: $type")}
                }
            }
    }
}