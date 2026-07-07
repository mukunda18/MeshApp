package com.minor.routing

import com.minor.model.HeaderProtocol
import com.minor.model.NodeId
import com.minor.model.NodesStore
import com.minor.model.Packet
import com.minor.model.PacketVerifier
import com.minor.model.ParseResult
import com.minor.model.Payload
import com.minor.logger.MeshLogger
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
    private val nodesStore: NodesStore,
    private val verifier: PacketVerifier? = null,
    private val freshnessWindowMs: Long
) {
    private data class RreqSession(
        val upstreamNodeId: NodeId,
        val createdAtMs: Long
    )

    /** Key is Pair(PacketType, PacketId), Value is createdTimestampMs */
    private val handledPackets = ConcurrentHashMap<Pair<Int, Long>, Long>()

    /**
     * Maps rreqId to the immediate upstream NodeId so RREP can be routed back
     * Upstream is the node this device received the RREQ from not the originator
     */
    private val rreqSessionTable = ConcurrentHashMap<Long, RreqSession>()

    /** Delivers MSG payloads addressed to this node to the MessagingService layer */
    val incomingPayloadChannel = Channel<Pair<NodeId, Payload.Message>>(capacity = Channel.UNLIMITED)

    /**
     * Entry point called by RoutingModule for every packet received from transport
     * senderIp must be the IP of the node that sent this specific datagram or TCP segment
     */
    suspend fun onPacketReceived(packet: Packet, senderIp: String) {
        try {
            val now = System.currentTimeMillis()
            
            // 1. Discard packets from ourselves (loopback)
            if (packet.header.sourceNodeId.bytes.contentEquals(selfNodeId.bytes)) {
                return
            }

            // 2. Discard stale packets
            if (now - packet.header.originTimestamp.millis > freshnessWindowMs) {
                MeshLogger.packetReceived("Receiver", "Dropped stale packet ${packet.header.id}", "From: ${packet.header.sourceNodeId}, Age: ${now - packet.header.originTimestamp.millis}ms")
                return
            }

            // 3. Discard already handled packets (De-duplication)
            val packetKey = Pair(packet.header.type, packet.header.id.value)
            if (handledPackets.putIfAbsent(packetKey, now) != null) {
                // Already handled this specific packet type + ID recently
                return
            }
            
            // Clean up old sessions and handled lists periodically
            pruneInternalState(now)

            MeshLogger.packetReceived("Receiver", "Received ${packet.header.type} packet ${packet.header.id}", "From IP: $senderIp, Source: ${packet.header.sourceNodeId}")
        
            when (packet.header.type) {
                HeaderProtocol.Type.HELLO -> handleHello(packet, senderIp)
                HeaderProtocol.Type.MESSAGE -> handleMessage(packet, senderIp)
                HeaderProtocol.Type.RREQ -> handleRreq(packet, senderIp)
                HeaderProtocol.Type.RREP -> handleRrep(packet, senderIp)
                HeaderProtocol.Type.ACK -> handleAck(packet, senderIp)
                HeaderProtocol.Type.RERR -> handleRerr(packet, senderIp)
            }
        } catch (e: Exception) {
            // Generic catch-all to prevent a malformed or malicious packet from crashing
            // the entire mesh receiver loop.
            android.util.Log.e("Receiver", "Failed to process packet from $senderIp", e)
            MeshLogger.error("Receiver", "Failed to process packet from $senderIp", e.toString())
        }
    }

    private fun handleHello(packet: Packet, senderIp: String) {
        try {
            val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: return
            val hello = result.value as? Payload.Hello ?: return
            
            nodesStore.addOrUpdateNode(packet.header.sourceNodeId, hello.name, hello.publicKey)
            
            peers.addOrUpdate(packet.header.sourceNodeId, senderIp)
            updateRouteFromHeader(packet, senderIp)
            
            for (entry in hello.routeEntries) {
                // Never add a route to ourselves
                if (entry.nodeId.bytes.contentEquals(selfNodeId.bytes)) continue
                
                // Do not add a routed path if the node is already a direct neighbor
                if (peers.isDirectPeer(entry.nodeId)) continue

                nodesStore.addOrUpdateNode(entry.nodeId, entry.name, entry.publicKey)
                router.update(entry.nodeId,
                    packet.header.sourceNodeId,
                    entry.hopcount + 1,
                    entry.timestamp.millis)
            }
        } catch (e: Exception) {
            MeshLogger.error("Receiver", "Error handling HELLO", e.toString())
        }
    }

    private suspend fun handleMessage(packet: Packet, senderIp: String) {
        try {
            updateRouteFromHeader(packet, senderIp)
            val h = packet.header
            val msgId = h.id.value

            if (h.destNodeId.bytes.contentEquals(selfNodeId.bytes)) {
                val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: run {
                    MeshLogger.error("Receiver", "Failed to parse MESSAGE $msgId payload")
                    return
                }
                val message = result.value as? Payload.Message ?: return
                
                MeshLogger.info("Receiver", "Accepted MESSAGE $msgId", "Source: ${packet.header.sourceNodeId}")
                incomingPayloadChannel.trySend(packet.header.sourceNodeId to message)
                sender.sendAck(msgId, h.sourceNodeId, 0x00)
                return
            }
            
            if (h.hopcount >= h.ttl) {
                MeshLogger.info("Receiver", "Discarded MESSAGE $msgId: TTL expired", "Hopcount: ${h.hopcount}, TTL: ${h.ttl}")
                return
            }
            
            val nextHop = router.lookup(h.destNodeId) ?: run {
                MeshLogger.info("Receiver", "Discarded MESSAGE $msgId: No route to ${h.destNodeId}")
                return
            }
            val nextIp = peers.resolveIp(nextHop) ?: run {
                MeshLogger.info("Receiver", "Discarded MESSAGE $msgId: Peer $nextHop offline")
                return
            }
            sender.forwardTcp(rebuildWithHop(packet, h.hopcount + 1), nextIp)
        } catch (e: Exception) {
            MeshLogger.error("Receiver", "Error handling MESSAGE", e.toString())
        }
    }

    private suspend fun handleRreq(packet: Packet, senderIp: String) {
        try {
            val h = packet.header
            val rreqId = h.id.value
            val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: return
            val rreq = result.value as? Payload.RREQ ?: return

            nodesStore.addOrUpdateNode(h.sourceNodeId, rreq.name, rreq.publicKey)

            // When hopcount is 0 the packet came directly from the originator so we can update peer table
            if (h.hopcount == 0) {
                peers.addOrUpdate(h.sourceNodeId, senderIp)
            }
            updateRouteFromHeader(packet, senderIp)

            // Upstream is whoever sent this packet to us which we look up by senderIp
            val upstreamNode = peers.getPeers()
                .firstOrNull { it.ip == senderIp }?.nodeId ?: return
            rreqSessionTable[rreqId] = RreqSession(
                upstreamNodeId = upstreamNode,
                createdAtMs = System.currentTimeMillis()
            )

            val weAreDestination = h.destNodeId.bytes.contentEquals(selfNodeId.bytes)
            val weHaveRoute = router.lookup(h.destNodeId) != null
            
            if (weAreDestination || weHaveRoute) {
                val upstreamIp = peers.resolveIp(upstreamNode) ?: return
                // RREP destination is the RREQ originator and the immediate send target is upstream
                sender.sendRrep(rreqId, h.sourceNodeId, upstreamIp)
            } else {
                if (h.hopcount >= h.ttl) {
                    MeshLogger.info("Receiver", "Discarded RREQ $rreqId: TTL expired")
                    return
                }
                sender.forwardUdpBroadcast(rebuildWithHop(packet, h.hopcount + 1))
            }
        } catch (e: Exception) {
            MeshLogger.error("Receiver", "Error handling RREQ", e.toString())
        }
    }

    private suspend fun handleRrep(packet: Packet, senderIp: String) {
        try {
            val h = packet.header
            val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: return
            val rrep = result.value as? Payload.RREP ?: return

            nodesStore.addOrUpdateNode(h.sourceNodeId, rrep.name, rrep.publicKey)

            if (h.hopcount == 0) {
                peers.addOrUpdate(h.sourceNodeId, senderIp)
            }

            updateRouteFromHeader(packet, senderIp)

            if (h.destNodeId.bytes.contentEquals(selfNodeId.bytes)) return
            
            if (h.hopcount >= h.ttl) {
                MeshLogger.info("Receiver", "Discarded RREP ${h.id}: TTL expired")
                return
            }

            // Forward upstream along the reverse path recorded when we saw the RREQ
            val upstream = rreqSessionTable.remove(h.id.value) ?: run {
                MeshLogger.info("Receiver", "Discarded RREP ${h.id}: No RREQ session found")
                return
            }
            val upstreamNode = upstream.upstreamNodeId
            val upstreamIp = peers.resolveIp(upstreamNode) ?: run {
                MeshLogger.info("Receiver", "Discarded RREP ${h.id}: Upstream $upstreamNode offline")
                return
            }
            sender.forwardTcp(rebuildWithHop(packet, h.hopcount + 1), upstreamIp)
        } catch (e: Exception) {
            MeshLogger.error("Receiver", "Error handling RREP", e.toString())
        }
    }

    private fun pruneInternalState(nowMs: Long) {
        // Prune RREQ sessions
        rreqSessionTable.entries.removeIf { (_, session) ->
            nowMs - session.createdAtMs > freshnessWindowMs
        }
        // Prune handled packets list
        handledPackets.entries.removeIf { (_, createdAt) ->
            nowMs - createdAt > freshnessWindowMs
        }
    }

    private fun handleAck(packet: Packet, senderIp: String) {
        try {
            updateRouteFromHeader(packet, senderIp)
            val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: return
            val ack = result.value as? Payload.Ack ?: return
            
            val valid = verifier?.verifyAck(
                packet.header.id,
                ack.status,
                ack.signature,
                packet.header.sourceNodeId
            ) ?: true

            if (valid && ack.status == 0x00) {
                sender.onAckReceived(packet.header.id.value)
            }
        } catch (e: Exception) {
            MeshLogger.error("Receiver", "Error handling ACK", e.toString())
        }
    }

    private suspend fun handleRerr(packet: Packet, senderIp: String) {
        try {
            updateRouteFromHeader(packet, senderIp)
            val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: return
            val rerr = result.value as? Payload.RERR ?: return
            val affected = rerr.destinations.filter {
                router.lookup(it)?.bytes?.contentEquals(packet.header.sourceNodeId.bytes) == true
            }
            affected.forEach { router.invalidate(it) }
            if (affected.isNotEmpty()) sender.broadcastRerr(affected)
        } catch (e: Exception) {
            MeshLogger.error("Receiver", "Error handling RERR", e.toString())
        }
    }

    /** Rebuilds packet bytes with an updated hop count without touching the payload */
    private fun rebuildWithHop(packet: Packet, newHopCount: Int): ByteArray {
        val newHeader = packet.header.copy(hopcount = newHopCount)
        val out = ByteArray(HeaderProtocol.HEADER_SIZE + packet.payload.size)
        HeaderSerializer.serialize(newHeader, out, 0)
        packet.payload.copyInto(out, HeaderProtocol.HEADER_SIZE)
        return out
    }

    private fun updateRouteFromHeader(packet: Packet, senderIp: String) {
        val h = packet.header
        
        // Never add a route to ourselves
        if (h.sourceNodeId.bytes.contentEquals(selfNodeId.bytes)) return

        val immediateSender = peers.getPeers().find { it.ip == senderIp }?.nodeId
            ?: if (h.hopcount == 0) h.sourceNodeId else return
        
        // Ensure immediate sender is not ourselves either
        if (immediateSender.bytes.contentEquals(selfNodeId.bytes)) return

        router.update(h.sourceNodeId, immediateSender, h.hopcount, h.originTimestamp.millis)
    }
}
