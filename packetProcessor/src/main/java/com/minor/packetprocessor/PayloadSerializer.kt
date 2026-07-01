package com.minor.packetprocessor

import com.minor.model.AckProtocol
import com.minor.model.HelloProtocol
import com.minor.model.MessageProtocol
import com.minor.model.Payload
import com.minor.model.RERRProtocol
import com.minor.model.RREPProtocol
import com.minor.model.RREQProtocol

object PayloadSerializer {
    fun serialize(payload: Payload, buffer: ByteArray, offset: Int): Int {
        return when (payload) {
            is Payload.Hello -> serializeHello(payload, buffer, offset)
            is Payload.Message -> serializeMessage(payload, buffer, offset)
            is Payload.Ack -> serializeAck(payload, buffer, offset)
            is Payload.RREQ -> serializeRreq(payload, buffer, offset)
            is Payload.RREP -> serializeRrep(payload, buffer, offset)
            is Payload.RERR -> serializeRerr(payload, buffer, offset)
        }
    }

    private fun serializeHello(payload: Payload.Hello, buffer: ByteArray, offset: Int): Int {
        var cursor = offset
        cursor += HelloProtocol.name.write(buffer, payload.name, cursor)
        cursor += HelloProtocol.publicKey.write(buffer, payload.publicKey, cursor)
        cursor += HelloProtocol.routeEntries.write(buffer, payload.routeEntries, cursor)
        return cursor - offset
    }

    private fun serializeMessage(payload: Payload.Message, buffer: ByteArray, offset: Int): Int {
        var cursor = offset
        cursor += MessageProtocol.messageId.write(buffer, payload.messageId, cursor)
        cursor += MessageProtocol.timestamp.write(buffer, payload.timestamp, cursor)
        cursor += MessageProtocol.content.write(buffer, payload.content, cursor)
        return cursor - offset
    }

    private fun serializeAck(payload: Payload.Ack, buffer: ByteArray, offset: Int): Int {
        var cursor = offset
        cursor += AckProtocol.status.write(buffer, payload.status, cursor)
        cursor += AckProtocol.signature.write(buffer, payload.signature, cursor)
        return cursor - offset
    }

    private fun serializeRreq(payload: Payload.RREQ, buffer: ByteArray, offset: Int): Int {
        var cursor = offset
        cursor += RREQProtocol.name.write(buffer, payload.name, cursor)
        cursor += RREQProtocol.publicKey.write(buffer, payload.publicKey, cursor)
        return cursor - offset
    }

    private fun serializeRrep(payload: Payload.RREP, buffer: ByteArray, offset: Int): Int {
        var cursor = offset
        cursor += RREPProtocol.name.write(buffer, payload.name, cursor)
        cursor += RREPProtocol.publicKey.write(buffer, payload.publicKey, cursor)
        return cursor - offset
    }

    private fun serializeRerr(payload: Payload.RERR, buffer: ByteArray, offset: Int): Int {
        return RERRProtocol.destinations.write(buffer, payload.destinations, offset)
    }
}
