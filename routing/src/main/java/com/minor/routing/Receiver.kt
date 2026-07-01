package com.minor.routing

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel

/** Key used to deduplicate handled packets by type and id */
data class HandledKey(val type: Byte, val id: Long)

/**
 * Reads packets converted from raw bytes and dispatches them per type
 * Updates the peer table and routing table and forwards packets that are not for this node
 */
class Receiver(
    private val selfNodeID: String,
    private val router: Router,
    private val peers: PeersManagement,
    private val sender: Sender,
    private val transport: TransportLink,
    private val builder: PacketBuilder,
    private val freshnessWindowMs: Long = 30_000
) {

    private val handled = ConcurrentHashMap<HandledKey, Boolean>()
    private val rreqSessionTable = ConcurrentHashMap<Long, String>()

    val incomingMessageChannel = Channel<Packet>(capacity = Channel.UNLIMITED)

    /** Entry point invoked by the transport layer for every raw inbound packet */
    fun onPacketReceived(packet: Packet, senderIp: String) {
        val header = packet.header
        val now = System.currentTimeMillis()
        if (now - header.originTimestamp > freshnessWindowMs) {
            return
        }

        when (header.type) {
            PacketType.HELLO -> handleHello(packet, senderIp)
            PacketType.MSG -> handleMsg(packet)
            PacketType.RREQ -> handleRreq(packet)
            PacketType.RREP -> handleRrep(packet, senderIp)
            PacketType.ACK -> handleAck(packet)
            PacketType.RERR -> handleRerr(packet)
        }
    }

    private fun resolveNodeIdByIp(ip: String): String? =
        peers.getPeers().firstOrNull { it.ip == ip }?.nodeID

    private fun handleHello(packet: Packet, senderIp: String) {
        val payload = decodeHello(packet.payload)
        val derivedID = sha256Hex(payload.publicKey)
        if (derivedID != packet.header.sourceNodeID) {
            return
        }
        peers.addOrUpdate(packet.header.sourceNodeID, senderIp, payload.name)
        for (entry in payload.routes) {
            router.update(entry.nodeID, packet.header.sourceNodeID, entry.hopCount + 1)
        }
    }

    private fun handleMsg(packet: Packet) {
        val key = HandledKey(PacketType.MSG, packet.header.id)
        if (handled.containsKey(key)) return
        handled[key] = true

        if (packet.header.destinationNodeID == selfNodeID) {
            incomingMessageChannel.trySend(packet)
            return
        }

        val header = packet.header
        if (header.hopCount <= 0) return
        header.hopCount -= 1
        val nextHop = router.lookup(header.destinationNodeID) ?: return
        sender.sendMessage(header.id, packet.payload, header.destinationNodeID)
        // forwarding reuses the queue so the next hop is resolved by Sender from the routing table
    }

    private fun handleRreq(packet: Packet) {
        val header = packet.header
        val key = HandledKey(PacketType.RREQ, header.id)
        if (handled.containsKey(key)) return
        handled[key] = true

        if (header.destinationNodeID == selfNodeID || router.lookup(header.destinationNodeID) != null) {
            val replyHopCount = router.lookup(header.destinationNodeID)?.let { 0 } ?: 0
            val rrep = builder.buildRREP(header.id, header.sourceNodeID, replyHopCount)
            val upstreamIp = peers.resolveIp(header.sourceNodeID)
            if (upstreamIp != null) {
                transport.sendTcp(builder.serialize(rrep), upstreamIp)
            }
        } else {
            rreqSessionTable[header.id] = header.sourceNodeID
            header.hopCount += 1
            if (header.hopCount < header.ttl) {
                transport.sendUdpBroadcast(builder.serialize(packet))
            }
        }
    }

    private fun handleRrep(packet: Packet, senderIp: String) {
        val header = packet.header
        val immediateSender = resolveNodeIdByIp(senderIp) ?: header.sourceNodeID
        router.update(header.sourceNodeID, immediateSender, header.hopCount)
        if (header.destinationNodeID != selfNodeID) {
            val upstream = rreqSessionTable[header.id] ?: return
            header.hopCount += 1
            val upstreamIp = peers.resolveIp(upstream)
            if (upstreamIp != null) {
                transport.sendTcp(builder.serialize(packet), upstreamIp)
            }
        }
    }

    private fun handleAck(packet: Packet) {
        val status = if (packet.payload.isNotEmpty()) packet.payload[0].toInt() else -1
        if (status == 0x00) {
            sender.onAckReceived(packet.header.id)
        }
    }

    private fun handleRerr(packet: Packet) {
        val payload = decodeRerr(packet.payload)
        val locallyAffected = mutableListOf<String>()
        for (nodeID in payload.destinations) {
            val nextHop = router.lookup(nodeID)
            if (nextHop == packet.header.sourceNodeID) {
                router.invalidate(nodeID)
                locallyAffected.add(nodeID)
            }
        }
        if (locallyAffected.isNotEmpty()) {
            sender.broadcastRerr(locallyAffected)
        }
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun decodeHello(bytes: ByteArray): HelloPayload {
        if (bytes.isEmpty()) return HelloPayload("", ByteArray(0), emptyList())
        var offset = 0
        val nameLen = bytes[offset].toInt() and 0xFF
        offset += 1
        val name = String(bytes, offset, nameLen, Charsets.UTF_8)
        offset += nameLen
        val publicKey = bytes.copyOfRange(offset, offset + 32)
        offset += 32
        val routeCount = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        offset += 2
        val routes = mutableListOf<RouteEntry>()
        repeat(routeCount) {
            val nodeIdBytes = bytes.copyOfRange(offset, offset + 32)
            offset += 32
            val hop = bytes[offset].toInt() and 0xFF
            offset += 1
            val entryNameLen = bytes[offset].toInt() and 0xFF
            offset += 1
            val entryName = String(bytes, offset, entryNameLen, Charsets.UTF_8)
            offset += entryNameLen
            routes.add(RouteEntry(nodeIdBytes.joinToString("") { "%02x".format(it) }, hop, entryName))
        }
        return HelloPayload(name, publicKey, routes)
    }

    private fun decodeRerr(bytes: ByteArray): RerrPayload {
        if (bytes.isEmpty()) return RerrPayload(emptyList())
        val count = bytes[0].toInt() and 0xFF
        val destinations = mutableListOf<String>()
        var offset = 1
        repeat(count) {
            val nodeIdBytes = bytes.copyOfRange(offset, offset + 32)
            destinations.add(nodeIdBytes.joinToString("") { "%02x".format(it) })
            offset += 32
        }
        return RerrPayload(destinations)
    }
}