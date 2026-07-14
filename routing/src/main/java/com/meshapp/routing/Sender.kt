package com.meshapp.routing

import com.meshapp.model.Header
import com.meshapp.model.HeaderProtocol
import com.meshapp.model.MessageId
import com.meshapp.model.NodeId
import com.meshapp.security.NodesStore
import com.meshapp.security.PacketSigner
import com.meshapp.model.Payload
import com.meshapp.model.PublicKey
import com.meshapp.model.RouteEntry
import com.meshapp.model.Signature
import com.meshapp.model.Timestamp
import com.meshapp.model.randomMessageId
import com.meshapp.network.MeshTransport
import com.meshapp.packetprocessor.HeaderSerializer
import com.meshapp.packetprocessor.PayloadSerializer
import android.util.Log
import com.meshapp.logger.MeshLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns the outbound message queue and all packet construction
 * Uses direct peer delivery when the destination is a neighbour
 * Falls back to routed delivery and RREQ discovery when no route exists
 */
class Sender(
    private val selfNodeId: NodeId,
    private val selfPublicKey: PublicKey,
    private val selfName: String,
    private val transport: MeshTransport,
    private val router: Router,
    private val peers: PeersManagement,
    private val nodesStore: NodesStore,
    private val signer: PacketSigner? = null,
    private val rreqRetryTimeoutMs: Long,
    private val maxHopCount: Int,
    private val routeRetryBackoffMs: Long
) {
    internal val channel = Channel<QueuedMessage>(capacity = Channel.UNLIMITED)
    private val pendingAck = ConcurrentHashMap<MessageId, QueuedMessage>()

    /** Status events emitted as each message progresses through its lifecycle */
    val statusChannel = Channel<Pair<MessageId, SendStatus>>(capacity = Channel.UNLIMITED)

    private val pendingDiscovery = ConcurrentHashMap<NodeId, Long>()

    /** Adds a message to the back of the outbound queue */
    fun enqueue(messageId: MessageId, payload: Payload.Message, destinationNodeId: NodeId) {
        if (destinationNodeId == selfNodeId) {
            MeshLogger.error("Sender", "Ignoring attempt to enqueue message to self")
            return
        }
        MeshLogger.messageQueued("Sender", "Enqueuing message ${messageId} for ${destinationNodeId}")
        channel.trySend(QueuedMessage(messageId, payload, destinationNodeId))
    }

    /** Builds and broadcasts a HELLO carrying the current valid route snapshot */
    suspend fun broadcastHello(displayName: String) {
        val directPeers = peers.getPeers()
        val directPeerIds = directPeers.map { it.nodeId }.toSet()

        val peerEntries = directPeers.mapNotNull { peer ->
            val pubKey = nodesStore.getPublicKey(peer.nodeId)
            if (pubKey == null) {
                MeshLogger.error("Sender", "Missing public key for direct peer ${peer.nodeId}, skipping from HELLO")
                null
            } else {
                val name = nodesStore.getName(peer.nodeId) ?: ""
                RouteEntry(peer.nodeId, 0, pubKey, Timestamp(peer.lastSeen), name)
            }
        }

        val routedEntries = router.getRoutes()
            .filter { it.hopCount <= maxHopCount - 1 }
            .filter { it.destinationNodeId !in directPeerIds }
            .mapNotNull { route ->
                val pubKey = nodesStore.getPublicKey(route.destinationNodeId)
                if (pubKey == null) {
                    null
                } else {
                    val name = nodesStore.getName(route.destinationNodeId) ?: ""
                    RouteEntry(route.destinationNodeId, route.hopCount, pubKey, Timestamp(route.routeTimestamp), name)
                }
            }

        val combinedRoutes = peerEntries + routedEntries

        transport.broadcastUdp(
            buildPacket(
                type = HeaderProtocol.Type.HELLO,
                flags = HeaderProtocol.Flags.BROADCAST,
                dest = NodeId(ByteArray(32)),
                id = randomMessageId(),
                hopCount = 0,
                payload = Payload.Hello(displayName, selfPublicKey, combinedRoutes)
            )
        )
        MeshLogger.packetSent("Sender", "Broadcasted HELLO", "Display Name: $displayName, Routes: ${combinedRoutes.size}")
    }

    /** Builds and broadcasts a RERR listing each newly unreachable destination */
    suspend fun broadcastRerr(unreachable: List<NodeId>) {
        if (unreachable.isEmpty()) return
        transport.broadcastUdp(
            buildPacket(
                type = HeaderProtocol.Type.RERR,
                flags = HeaderProtocol.Flags.BROADCAST,
                dest = NodeId(ByteArray(32)),
                id = randomMessageId(),
                hopCount = 0,
                payload = Payload.RERR(unreachable)
            )
        )
        MeshLogger.packetSent("Sender", "Broadcasted RERR", "Unreachable nodes: ${unreachable.size}")
    }

    /**
     * Sends an RREP back toward the RREQ originator via the given upstream IP
     * The dest field is set to the originator so intermediate nodes can forward it correctly
     */
    suspend fun sendRrep(rreqId: MessageId, rreqOriginatorNodeId: NodeId, upstreamIp: String) {
        try {
            transport.sendTcp(
                buildPacket(
                    type = HeaderProtocol.Type.RREP,
                    flags = 0,
                    dest = rreqOriginatorNodeId,
                    id = rreqId,
                    hopCount = 0,
                    payload = Payload.RREP(selfName, selfPublicKey)
                ),
                upstreamIp
            )
        } catch (e: Exception) {
            Log.w("Sender", "Failed to send RREP to $upstreamIp", e)
            MeshLogger.error("Sender", "Failed to send RREP to $upstreamIp", e.toString())
        }
    }

    /** Sends a signed ACK for the given messageId back toward the original sender */
    suspend fun sendAck(messageId: MessageId, destNodeId: NodeId, status: Int) {
        val directIp = if (peers.isDirectPeer(destNodeId)) peers.resolveIp(destNodeId) else null
        val routedNextHop = router.lookup(destNodeId)
        val routedIp = routedNextHop?.let { peers.resolveIp(it) }

        val ip = directIp ?: routedIp ?: run {
            MeshLogger.error("Sender", "Failed to send ACK: No route to $destNodeId")
            return
        }

        val signature = signer?.signAck(messageId, status) ?: Signature(ByteArray(64))
        try {
            transport.sendTcp(
                buildPacket(
                    type = HeaderProtocol.Type.ACK,
                    flags = 0,
                    dest = destNodeId,
                    id = messageId,
                    hopCount = 0,
                    payload = Payload.Ack(status, signature)
                ),
                ip
            )
            MeshLogger.packetSent("Sender", "Sent ACK for $messageId to $destNodeId", "IP: $ip, Status: $status")
        } catch (e: Exception) {
            Log.w("Sender", "Failed to send ACK to $ip", e)
            MeshLogger.error("Sender", "Failed to send ACK to $ip", e.toString())
        }
    }

    /** Passes raw pre-built bytes directly to the TCP transport */
    suspend fun forwardTcp(bytes: ByteArray, ip: String) {
        try {
            transport.sendTcp(bytes, ip)
        } catch (e: Exception) {
            Log.w("Sender", "Failed to forward TCP to $ip", e)
            MeshLogger.error("Sender", "Failed to forward TCP to $ip", e.toString())
        }
    }

    /** Passes raw pre-built bytes to the UDP broadcast transport */
    suspend fun forwardUdpBroadcast(bytes: ByteArray) {
        try {
            transport.broadcastUdp(bytes)
        } catch (e: Exception) {
            Log.w("Sender", "Failed to forward UDP broadcast", e)
            MeshLogger.error("Sender", "Failed to forward UDP broadcast", e.toString())
        }
    }

    /** Called by Receiver when a verified ACK arrives for a pending message */
    fun onAckReceived(messageId: MessageId) {
        pendingAck.remove(messageId)?.let {
            statusChannel.trySend(messageId to SendStatus.DELIVERED)
        }
    }

    /** Starts the coroutine that continuously drains and processes the outbound queue */
    fun startQueueLoop(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                try {
                    val msg = channel.receive()
                    processMessage(msg, scope)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // Prevent the queue loop from dying due to unexpected logic errors.
                    // Possible exceptions:
                    // - IllegalStateException (from transport or router)
                    // - NullPointerException (unexpected state)
                    Log.e("Sender", "Error in sender queue loop", e)
                    delay(100.milliseconds) // Cooling-off period
                }
            }
        }
    }

    /**
     * Constructs a complete serialized packet from a Header and Payload
     * First serializes the payload to measure its byte length then builds the Header
     * Returns a single ByteArray of HEADER SIZE plus payload bytes
     */
    fun buildPacket(
        type: Int,
        flags: Int,
        dest: NodeId,
        id: MessageId,
        hopCount: Int,
        payload: Payload
    ): ByteArray {
        val buf = ByteArray(65_536)
        val payloadLen = PayloadSerializer.serialize(payload,
            buf,
            HeaderProtocol.HEADER_SIZE)
        val header = Header(
            magic = HeaderProtocol.Magic.EXPECTED,
            version = HeaderProtocol.Version.SUPPORTED_VERSION,
            type = type,
            flags = flags,
            hopcount = hopCount,
            ttl = maxHopCount,
            reserved = 0,
            immediateSenderNodeId = selfNodeId,
            sourceNodeId = selfNodeId,
            destNodeId = dest,
            id = id,
            originTimestamp = Timestamp(System.currentTimeMillis()),
            payloadLength = payloadLen
        )
        HeaderSerializer.serialize(header, buf, 0)
        return buf.copyOfRange(0, HeaderProtocol.HEADER_SIZE + payloadLen)
    }

    internal suspend fun processMessage(msg: QueuedMessage, scope: CoroutineScope) {
        val now = System.currentTimeMillis()

        val directIp = if (peers.isDirectPeer(msg.destinationNodeId))
            peers.resolveIp(msg.destinationNodeId) else null
        val routedNextHop = router.lookup(msg.destinationNodeId)
        val routedIp = routedNextHop?.let { peers.resolveIp(it) }
        val targetIp = directIp ?: routedIp

        if (targetIp != null) {
            deliverTo(msg, targetIp)
            return
        }

        if (now - msg.enqueueTime > rreqRetryTimeoutMs) {
            statusChannel.trySend(msg.messageId to SendStatus.FAILED)
            MeshLogger.messageDropped("Sender", "Message ${msg.messageId} failed: Discovery timeout", "To: ${msg.destinationNodeId}")
            pendingDiscovery.remove(msg.destinationNodeId)
            return
        }

        val lastDiscovery = pendingDiscovery[msg.destinationNodeId]
        if (lastDiscovery == null || now - lastDiscovery > routeRetryBackoffMs) {
            MeshLogger.info("Sender", "Issuing RREQ for ${msg.destinationNodeId}")
            issueRreq(msg.destinationNodeId)
            pendingDiscovery[msg.destinationNodeId] = now
        }

        scope.launch {
            delay(routeRetryBackoffMs.milliseconds)
            channel.send(msg)
        }
    }

    private suspend fun deliverTo(msg: QueuedMessage, ip: String) {
        val bytes = buildPacket(
            type = HeaderProtocol.Type.MESSAGE,
            flags = HeaderProtocol.Flags.ENCRYPTED or HeaderProtocol.Flags.ACK_REQUESTED,
            dest = msg.destinationNodeId,
            id = msg.messageId,
            hopCount = 0,
            payload = msg.payload
        )
        try {
            transport.sendTcp(bytes, ip)
            pendingAck[msg.messageId] = msg
            statusChannel.trySend(msg.messageId to SendStatus.SENT)
            MeshLogger.packetSent("Sender", "Sent MESSAGE ${msg.messageId} to ${msg.destinationNodeId}", "IP: $ip")
        } catch (_: Exception) {
            statusChannel.trySend(msg.messageId to SendStatus.FAILED)
            MeshLogger.error("Sender", "Failed to send MESSAGE ${msg.messageId} to $ip")
        }
    }

    /** Issues a RREQ to discover a route (and identity) for the given node */
    suspend fun discover(destinationNodeId: NodeId) {
        MeshLogger.info("Sender", "Issuing RREQ for $destinationNodeId")
        issueRreq(destinationNodeId)
    }

    private suspend fun issueRreq(destinationNodeId: NodeId) {
        transport.broadcastUdp(
            buildPacket(
                type = HeaderProtocol.Type.RREQ,
                flags = HeaderProtocol.Flags.BROADCAST,
                dest = destinationNodeId,
                id = randomMessageId(),
                hopCount = 0,
                payload = Payload.RREQ(selfName, selfPublicKey)
            )
        )
    }
}
