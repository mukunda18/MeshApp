package com.minor.routing

import com.minor.model.HeaderProtocol
import com.minor.model.NodeId
import com.minor.model.Packet
import com.minor.model.ParseResult
import com.minor.model.Payload
import com.minor.packetprocessor.HeaderSerializer
import com.minor.packetprocessor.PayloadParser
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel

/**
 * Dispatches each inbound Packet to the correct handler based on header type
 * Requires the caller to supply the senderIp alongside every packet because the
 * current TCPReceiver and UdpSocket channels do not expose the source address
 * MeshControl is responsible for extracting and pairing the IP before calling onPacketReceived
 */
class Receiver(
    private val selfNodeId: NodeId,
    private val router: Router,
    private val peers: PeersManagement,
    private val sender: Sender,
    private val freshnessWindowMs: Long = 30_000
) {
    private val handledMsg = ConcurrentHashMap<Long, Boolean>()
    private val handledRreq = ConcurrentHashMap<Long, Boolean>()

    /**
     * Maps rreqId to the immediate upstream NodeId so RREP can be routed back
     * Upstream is the node this device received the RREQ from not the originator
     */
    private val rreqSessionTable = ConcurrentHashMap<Long, NodeId>()

    /** Delivers MSG packets addressed to this node to the MessagingService layer */
    val incomingMessageChannel = Channel<Packet>(capacity = Channel.UNLIMITED)

    /**
     * Entry point called by RoutingModule for every packet received from transport
     * senderIp must be the IP of the node that sent this specific datagram or TCP segment
     */
    suspend fun onPacketReceived(packet: Packet, senderIp: String) {
        val now = System.currentTimeMillis()
        if (now - packet.header.originTimestamp.millis > freshnessWindowMs) return
        when (packet.header.type) {
            HeaderProtocol.Type.HELLO -> handleHello(packet, senderIp)
            HeaderProtocol.Type.MESSAGE -> handleMessage(packet, senderIp)
            HeaderProtocol.Type.RREQ -> handleRreq(packet, senderIp)
            HeaderProtocol.Type.RREP -> handleRrep(packet, senderIp)
            HeaderProtocol.Type.ACK -> handleAck(packet, senderIp)
            HeaderProtocol.Type.RERR -> handleRerr(packet, senderIp)
        }
    }

    private fun handleHello(packet: Packet, senderIp: String) {
        val result = PayloadParser.parse(packet) as? ParseResult.Success ?: return
        val hello = result.value as? Payload.Hello ?: return
        peers.addOrUpdate(packet.header.sourceNodeId, senderIp, hello.name, hello.publicKey)
        updateRouteFromHeader(packet, senderIp, hello.name)
        for (entry in hello.routeEntries) {
            router.update(entry.nodeId, entry.name,
                packet.header.sourceNodeId,
                entry.hopcount + 1,
                entry.timestamp.millis)
        }
    }

    private suspend fun handleMessage(packet: Packet, senderIp: String) {
        updateRouteFromHeader(packet, senderIp)
        val msgId = packet.header.id.value
        if (handledMsg.putIfAbsent(msgId, true) != null) return
        val h = packet.header
        if (h.destNodeId.toString() == selfNodeId.toString()) {
            incomingMessageChannel.trySend(packet)
            sender.sendAck(msgId, h.sourceNodeId, 0x00)
            return
        }
        if (h.hopcount >= h.ttl) return
        val nextHop = router.lookup(h.destNodeId) ?: return
        val nextIp = peers.resolveIp(nextHop) ?: return
        sender.forwardTcp(rebuildWithHop(packet, h.hopcount + 1), nextIp)
    }

    private suspend fun handleRreq(packet: Packet, senderIp: String) {
        val rreqId = packet.header.id.value
        if (handledRreq.putIfAbsent(rreqId, true) != null) return
        val result = PayloadParser.parse(packet) as? ParseResult.Success ?: return
        val rreq = result.value as? Payload.RREQ ?: return
        val h = packet.header

        // When hopcount is 0 the packet came directly from the originator so we can verify it
        if (h.hopcount == 0) {
            peers.addOrUpdate(h.sourceNodeId, senderIp, rreq.name, rreq.publicKey)
        }
        updateRouteFromHeader(packet, senderIp, rreq.name)

        // Upstream is whoever sent this packet to us which we look up by senderIp
        val upstreamNode = peers.getPeers()
            .firstOrNull { it.ip == senderIp }?.nodeId ?: return
        rreqSessionTable[rreqId] = upstreamNode

        val weAreDestination = h.destNodeId.toString() == selfNodeId.toString()
        val weHaveRoute = router.lookup(h.destNodeId) != null
        if (weAreDestination || weHaveRoute) {
            val upstreamIp = peers.resolveIp(upstreamNode) ?: return
            // RREP destination is the RREQ originator and the immediate send target is upstream
            sender.sendRrep(rreqId, h.sourceNodeId, upstreamIp)
        } else {
            if (h.hopcount >= h.ttl) return
            sender.forwardUdpBroadcast(rebuildWithHop(packet, h.hopcount + 1))
        }
    }

    private suspend fun handleRrep(packet: Packet, senderIp: String) {
        val result = PayloadParser.parse(packet) as? ParseResult.Success ?: return
        val rrep = result.value as? Payload.RREP ?: return
        val h = packet.header

        peers.addOrUpdate(h.sourceNodeId, senderIp, rrep.name, rrep.publicKey)

        updateRouteFromHeader(packet, senderIp, rrep.name)

        if (h.destNodeId.toString() == selfNodeId.toString()) return

        // Forward upstream along the reverse path recorded when we saw the RREQ
        val upstream = rreqSessionTable[h.id.value] ?: return
        val upstreamIp = peers.resolveIp(upstream) ?: return
        sender.forwardTcp(rebuildWithHop(packet, h.hopcount + 1), upstreamIp)
    }

    private fun handleAck(packet: Packet, senderIp: String) {
        updateRouteFromHeader(packet, senderIp)
        val result = PayloadParser.parse(packet) as? ParseResult.Success ?: return
        val ack = result.value as? Payload.Ack ?: return
        if (ack.status == 0x00) sender.onAckReceived(packet.header.id.value)
    }

    private suspend fun handleRerr(packet: Packet, senderIp: String) {
        updateRouteFromHeader(packet, senderIp)
        val result = PayloadParser.parse(packet) as? ParseResult.Success ?: return
        val rerr = result.value as? Payload.RERR ?: return
        val senderKey = packet.header.sourceNodeId.toString()
        val affected = rerr.destinations.filter {
            router.lookup(it)?.toString() == senderKey
        }
        affected.forEach { router.invalidate(it) }
        if (affected.isNotEmpty()) sender.broadcastRerr(affected)
    }

    /** Rebuilds packet bytes with an updated hop count without touching the payload */
    private fun rebuildWithHop(packet: Packet, newHopCount: Int): ByteArray {
        val newHeader = packet.header.copy(hopcount = newHopCount)
        val out = ByteArray(HeaderProtocol.HEADER_SIZE + packet.payload.size)
        HeaderSerializer.serialize(newHeader, out, 0)
        packet.payload.copyInto(out, HeaderProtocol.HEADER_SIZE)
        return out
    }

    private fun updateRouteFromHeader(packet: Packet, senderIp: String, name: String? = null) {
        val h = packet.header
        val immediateSender = peers.getPeers().find { it.ip == senderIp }?.nodeId
            ?: if (h.hopcount == 0) h.sourceNodeId else return

        val effectiveName = name
            ?: peers.getPeers().find { it.nodeId == h.sourceNodeId }?.name
            ?: router.getRoutes().find { it.destinationNodeId == h.sourceNodeId }?.name
            ?: ""

        router.update(h.sourceNodeId, effectiveName, immediateSender, h.hopcount, h.originTimestamp.millis)
    }
}