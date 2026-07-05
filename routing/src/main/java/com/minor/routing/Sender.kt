package com.minor.routing

import com.minor.model.Header
import com.minor.model.HeaderProtocol
import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.NodesStore
import com.minor.model.PacketSigner
import com.minor.model.Payload
import com.minor.model.PublicKey
import com.minor.model.RouteEntry
import com.minor.model.Signature
import com.minor.model.Timestamp
import com.minor.model.randomMessageId
import com.minor.network.MeshTransport
import com.minor.packetprocessor.HeaderSerializer
import com.minor.packetprocessor.PayloadSerializer
import android.util.Log
import com.minor.logger.MeshLogger
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
    private val rreqRetryTimeoutMs: Long = 8_000,
    private val maxHopCount: Int = 8
) {
    internal val channel = Channel<QueuedMessage>(capacity = Channel.UNLIMITED)
    private val pendingAck = ConcurrentHashMap<Long, QueuedMessage>()

    /** Status events emitted as each message progresses through its lifecycle */
    val statusChannel = Channel<Pair<Long, SendStatus>>(capacity = Channel.UNLIMITED)

    /** Adds a message to the back of the outbound queue */
    fun enqueue(messageId: MessageId, payload: Payload.Message, destinationNodeId: NodeId) {
        MeshLogger.messageQueued("Sender", "Enqueuing message ${messageId} for ${destinationNodeId}")
        channel.trySend(QueuedMessage(messageId, payload, destinationNodeId))
    }

    /** Builds and broadcasts a HELLO carrying the current valid route snapshot */
    suspend fun broadcastHello(displayName: String) {
        val routes = router.getRoutes()
            .filter { it.hopCount <= maxHopCount - 1 }
            .map { route ->
                val pubKey = nodesStore.getPublicKey(route.destinationNodeId) ?: PublicKey(ByteArray(32))
                val name = nodesStore.getName(route.destinationNodeId) ?: ""
                RouteEntry(route.destinationNodeId, route.hopCount, pubKey, Timestamp(route.routeTimestamp), name)
            }
        transport.broadcastUdp(
            buildPacket(
                type = HeaderProtocol.Type.HELLO,
                flags = HeaderProtocol.Flags.BROADCAST,
                dest = NodeId(ByteArray(32)),
                id = randomMessageId(),
                hopCount = 0,
                payload = Payload.Hello(displayName, selfPublicKey, routes)
            )
        )
        MeshLogger.packetSent("Sender", "Broadcasted HELLO", "Display Name: $displayName, Routes: ${routes.size}")
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
    suspend fun sendRrep(rreqId: Long, rreqOriginatorNodeId: NodeId, upstreamIp: String) {
        try {
            transport.sendTcp(
                buildPacket(
                    type = HeaderProtocol.Type.RREP,
                    flags = 0,
                    dest = rreqOriginatorNodeId,
                    id = MessageId(rreqId),
                    hopCount = 0,
                    payload = Payload.RREP(selfName, selfPublicKey)
                ),
                upstreamIp
            )
        } catch (e: Exception) {
            Log.w("Sender", "Failed to send RREP to $upstreamIp", e)
        }
    }

    /** Sends a signed ACK for the given messageId back toward the original sender */
    suspend fun sendAck(messageId: Long, destNodeId: NodeId, status: Int) {
        val ip = peers.resolveIp(destNodeId) ?: return
        val signature = signer?.signAck(MessageId(messageId), status) ?: Signature(ByteArray(64))
        try {
            transport.sendTcp(
                buildPacket(
                    type = HeaderProtocol.Type.ACK,
                    flags = 0,
                    dest = destNodeId,
                    id = MessageId(messageId),
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
        }
    }

    /** Passes raw pre-built bytes to the UDP broadcast transport */
    suspend fun forwardUdpBroadcast(bytes: ByteArray) {
        try {
            transport.broadcastUdp(bytes)
        } catch (e: Exception) {
            Log.w("Sender", "Failed to forward UDP broadcast", e)
        }
    }

    /** Called by Receiver when a verified ACK arrives for a pending message */
    fun onAckReceived(messageId: Long) {
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
                    delay(100.milliseconds) // Cooling off period
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
            statusChannel.trySend(msg.messageId.value to SendStatus.FAILED)
            MeshLogger.messageDropped("Sender", "Message ${msg.messageId} failed: Discovery timeout", "To: ${msg.destinationNodeId}")
            return
        }

        if (!msg.rreqFlag) {
            MeshLogger.info("Sender", "Issuing RREQ for ${msg.destinationNodeId}")
            issueRreq(msg.destinationNodeId)
            msg.rreqFlag = true
        }

        scope.launch {
            delay(ROUTE_RETRY_BACKOFF_MS.milliseconds)
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
            pendingAck[msg.messageId.value] = msg
            statusChannel.trySend(msg.messageId.value to SendStatus.SENT)
            MeshLogger.packetSent("Sender", "Sent MESSAGE ${msg.messageId} to ${msg.destinationNodeId}", "IP: $ip")
        } catch (_: Exception) {
            statusChannel.trySend(msg.messageId.value to SendStatus.FAILED)
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

    companion object {
        const val ROUTE_RETRY_BACKOFF_MS = 500L
    }

}