package com.meshapp.packetprocessor

import com.meshapp.model.AckProtocol
import com.meshapp.model.HelloProtocol
import com.meshapp.model.MessageProtocol
import com.meshapp.model.Payload
import com.meshapp.model.RERRProtocol
import com.meshapp.model.RREPProtocol
import com.meshapp.model.RREQProtocol

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
        val env = payload.envelope
        cursor += MessageProtocol.envVersion.write(buffer, env.envVersion, cursor)
        cursor += MessageProtocol.senderNodeId.write(buffer, env.senderNodeId, cursor)
        cursor += MessageProtocol.encSymKey.write(buffer, env.encSymKey, cursor)
        cursor += MessageProtocol.nonce.write(buffer, env.nonce, cursor)
        cursor += MessageProtocol.ciphertext.write(buffer, env.ciphertext, cursor)
        cursor += MessageProtocol.signature.write(buffer, env.signature, cursor)
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
        cursor += RREPProtocol.Name.write(buffer, payload.name, cursor)
        cursor += RREPProtocol.PublicKey.write(buffer, payload.publicKey, cursor)
        return cursor - offset
    }

    private fun serializeRerr(payload: Payload.RERR, buffer: ByteArray, offset: Int): Int {
        return RERRProtocol.destinations.write(buffer, payload.destinations, offset)
    }
}
