package com.meshapp.routing

import com.meshapp.model.HeaderProtocol
import com.meshapp.model.NodeId
import com.meshapp.model.MessageId
import com.meshapp.security.NodesStore
import com.meshapp.model.Packet
import com.meshapp.model.Envelope
import com.meshapp.security.PacketVerifier
import com.meshapp.model.ParseResult
import com.meshapp.model.Payload
import com.meshapp.logger.MeshLogger
import com.meshapp.packetprocessor.HeaderSerializer
import com.meshapp.packetprocessor.PayloadParser
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
    /** Key is Pair(PacketType, PacketId), Value is createdTimestampMs */
    private val handledPackets = ConcurrentHashMap<Pair<Int, MessageId>, Long>()

    /** Delivers MSG payloads addressed to this node to the MessagingService layer */
    val incomingPayloadChannel = Channel<Pair<NodeId, Payload.Message>>(capacity = Channel.UNLIMITED)

    /**
     * Entry point called by RoutingModule for every packet received from transport
     * senderIp must be the IP of the node that sent this specific datagram or TCP segment
     */
    suspend fun onPacketReceived(packet: Packet, senderIp: String) {
        try {
            val now = System.currentTimeMillis()
            val h = packet.header
            
            // 1. Discard packets from ourselves (loopback)
            if (h.sourceNodeId.bytes.contentEquals(selfNodeId.bytes)) {
                return
            }

            // 2. Global Learning: Every packet identifies its immediate sender and the originator
            // Learn/Refresh neighbor's IP
            peers.addOrUpdate(h.immediateSenderNodeId, senderIp)
            
            // Passive Route Learning: Originator is reachable via the neighbor who sent this to us
            router.update(h.sourceNodeId, h.immediateSenderNodeId, h.hopcount, h.originTimestamp.millis)

            // 3. Discard stale packets
            if (now - h.originTimestamp.millis > freshnessWindowMs) {
                MeshLogger.packetReceived("Receiver", "Dropped stale packet ${h.id}", "From: ${h.sourceNodeId}, Age: ${now - h.originTimestamp.millis}ms")
                return
            }

            // 4. Discard already handled packets (De-duplication)
            val packetKey = Pair(h.type, h.id)
            if (handledPackets.putIfAbsent(packetKey, now) != null) {
                // Already handled this specific packet type + ID recently
                return
            }
            
            MeshLogger.packetReceived("Receiver", "Received ${h.type} packet ${h.id}", "From IP: $senderIp, Source: ${h.sourceNodeId}")
        
            when (h.type) {
                HeaderProtocol.Type.HELLO -> handleHello(packet)
                HeaderProtocol.Type.MESSAGE -> handleMessage(packet)
                HeaderProtocol.Type.RREQ -> handleRreq(packet)
                HeaderProtocol.Type.RREP -> handleRrep(packet)
                HeaderProtocol.Type.ACK -> handleAck(packet)
                HeaderProtocol.Type.RERR -> handleRerr(packet)
            }
        } catch (e: Exception) {
            // Generic catch-all to prevent a malformed or malicious packet from crashing
            // the entire mesh receiver loop.
            android.util.Log.e("Receiver", "Failed to process packet from $senderIp", e)
            MeshLogger.error("Receiver", "Failed to process packet from $senderIp", e.toString())
        }
    }

    private fun handleHello(packet: Packet) {
        try {
            val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: return
            val hello = result.value as? Payload.Hello ?: return
            
            nodesStore.addOrUpdateNode(packet.header.sourceNodeId, hello.name, hello.publicKey)
            
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

    private suspend fun handleMessage(packet: Packet) {
        try {
            val h = packet.header
            val msgId = h.id

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
                // Reactive RERR: Inform the source (and others) that this destination is unreachable
                sender.broadcastRerr(listOf(h.destNodeId))
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

    private suspend fun handleRreq(packet: Packet) {
        try {
            val h = packet.header
            val rreqId = h.id
            val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: return
            val rreq = result.value as? Payload.RREQ ?: return

            nodesStore.addOrUpdateNode(h.sourceNodeId, rreq.name, rreq.publicKey)

            val weAreDestination = h.destNodeId.bytes.contentEquals(selfNodeId.bytes)
            val weHaveRoute = router.lookup(h.destNodeId) != null
            
            if (weAreDestination || weHaveRoute) {
                // RREP destination is the RREQ originator (h.sourceNodeId)
                // The immediate next hop back is the guy who just sent us the RREQ
                val upstreamIp = peers.resolveIp(h.immediateSenderNodeId) ?: return
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

    private suspend fun handleRrep(packet: Packet) {
        try {
            val h = packet.header
            val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: return
            val rrep = result.value as? Payload.RREP ?: return

            nodesStore.addOrUpdateNode(h.sourceNodeId, rrep.name, rrep.publicKey)

            if (h.destNodeId.bytes.contentEquals(selfNodeId.bytes)) return
            
            if (h.hopcount >= h.ttl) {
                MeshLogger.info("Receiver", "Discarded RREP ${h.id}: TTL expired")
                return
            }

            // Forward toward the RREQ originator using our learned routes
            val nextHop = router.lookup(h.destNodeId) ?: run {
                MeshLogger.info("Receiver", "Discarded RREP ${h.id}: No route back to ${h.destNodeId}")
                return
            }
            val nextIp = peers.resolveIp(nextHop) ?: run {
                MeshLogger.info("Receiver", "Discarded RREP ${h.id}: Next hop $nextHop offline")
                return
            }
            sender.forwardTcp(rebuildWithHop(packet, h.hopcount + 1), nextIp)
        } catch (e: Exception) {
            MeshLogger.error("Receiver", "Error handling RREP", e.toString())
        }
    }

    /** Starts a background loop that periodically prunes the de-duplication cache */
    fun startPruneLoop(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(freshnessWindowMs.milliseconds)
                pruneInternalState(System.currentTimeMillis())
            }
        }
    }

    /** Starts the inbound packet processing loops for both TCP and UDP channels */
    fun startReceiveLoops(
        scope: CoroutineScope,
        tcpIncoming: ReceiveChannel<Envelope>,
        udpIncoming: ReceiveChannel<Envelope>
    ) {
        scope.launch {
            for (envelope in tcpIncoming) {
                try {
                    val ip = envelope.remoteAddress.address.hostAddress ?: continue
                    onPacketReceived(envelope.packet, ip)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("Receiver", "Error processing TCP packet", e)
                }
            }
        }
        scope.launch {
            for (envelope in udpIncoming) {
                try {
                    val ip = envelope.remoteAddress.address.hostAddress ?: continue
                    onPacketReceived(envelope.packet, ip)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("Receiver", "Error processing UDP packet", e)
                }
            }
        }
    }

    private fun pruneInternalState(nowMs: Long) {
        // Prune handled packets list
        handledPackets.entries.removeIf { (_, createdAt) ->
            nowMs - createdAt > freshnessWindowMs
        }
    }

    private suspend fun handleAck(packet: Packet) {
        try {
            val h = packet.header
            if (h.destNodeId.bytes.contentEquals(selfNodeId.bytes)) {
                val result = PayloadParser.parse(packet) as? ParseResult.Success<*> ?: return
                val ack = result.value as? Payload.Ack ?: return

                val valid = verifier?.verifyAck(
                    packet.header.id,
                    ack.status,
                    ack.signature,
                    packet.header.sourceNodeId
                ) ?: true

                if (valid && ack.status == 0x00) {
                    sender.onAckReceived(packet.header.id)
                }
                return
            }

            if (h.hopcount >= h.ttl) {
                MeshLogger.info("Receiver", "Discarded ACK ${h.id}: TTL expired")
                return
            }

            val nextHop = router.lookup(h.destNodeId) ?: run {
                MeshLogger.info("Receiver", "Discarded ACK ${h.id}: No route to ${h.destNodeId}")
                return
            }
            val nextIp = peers.resolveIp(nextHop) ?: run {
                MeshLogger.info("Receiver", "Discarded ACK ${h.id}: Peer $nextHop offline")
                return
            }
            sender.forwardTcp(rebuildWithHop(packet, h.hopcount + 1), nextIp)
        } catch (e: Exception) {
            MeshLogger.error("Receiver", "Error handling ACK", e.toString())
        }
    }

    private suspend fun handleRerr(packet: Packet) {
        try {
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

    /** Rebuilds packet bytes with an updated hop count and our NodeId as the immediate sender */
    private fun rebuildWithHop(packet: Packet, newHopCount: Int): ByteArray {
        val newHeader = packet.header.copy(
            hopcount = newHopCount,
            immediateSenderNodeId = selfNodeId
        )
        val out = ByteArray(HeaderProtocol.HEADER_SIZE + packet.payload.size)
        HeaderSerializer.serialize(newHeader, out, 0)
        packet.payload.copyInto(out, HeaderProtocol.HEADER_SIZE)
        return out
    }
}
