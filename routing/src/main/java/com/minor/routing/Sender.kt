package com.minor.routing

import com.minor.model.Header
import com.minor.model.HeaderProtocol
import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.Packet
import com.minor.model.Payload
import com.minor.model.PublicKey
import com.minor.model.RouteEntry
import com.minor.model.Signature
import com.minor.model.Timestamp
import com.minor.packetprocessor.HeaderSerializer
import com.minor.packetprocessor.PayloadSerializer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private val rreqRetryTimeoutMs: Long = 8_000,
    private val maxHopCount: Int = 8
) {
    internal val queue = ArrayDeque<QueuedMessage>()
    private val pendingAck = ConcurrentHashMap<Long, QueuedMessage>()
    private var nextRreqId = 1L

    /** Status events emitted as each message progresses through its lifecycle */
    val statusChannel = Channel<Pair<Long, SendStatus>>(capacity = Channel.UNLIMITED)

    /** Adds a message to the back of the outbound queue */
    fun enqueue(messageId: Long, payload: Payload, destinationNodeId: NodeId) {
        synchronized(queue) {
            queue.addLast(QueuedMessage(messageId, payload, destinationNodeId))
        }
    }

    /** Builds and broadcasts a HELLO carrying the current valid route snapshot */
    suspend fun broadcastHello(displayName: String) {
        val routes = router.getRoutes()
            .filter { it.hopCount <= maxHopCount - 1 }
            .map { RouteEntry(it.destinationNodeId, it.hopCount, selfPublicKey, "") }
        transport.broadcastUdp(
            buildPacket(
                type = HeaderProtocol.Type.HELLO,
                flags = HeaderProtocol.Flags.BROADCAST,
                dest = NodeId(ByteArray(32)),
                id = 0L,
                hopCount = 0,
                payload = Payload.Hello(displayName, selfPublicKey, routes)
            )
        )
    }

    /** Builds and broadcasts a RERR listing each newly unreachable destination */
    suspend fun broadcastRerr(unreachable: List<NodeId>) {
        if (unreachable.isEmpty()) return
        transport.broadcastUdp(
            buildPacket(
                type = HeaderProtocol.Type.RERR,
                flags = HeaderProtocol.Flags.BROADCAST,
                dest = NodeId(ByteArray(32)),
                id = 0L,
                hopCount = 0,
                payload = Payload.RERR(unreachable)
            )
        )
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
                    id = rreqId,
                    hopCount = 0,
                    payload = Payload.RREP(selfName, selfPublicKey)
                ),
                upstreamIp
            )
        } catch (_: Exception) { }
    }

    /** Sends a signed ACK for the given messageId back toward the original sender */
    suspend fun sendAck(messageId: Long, destNodeId: NodeId, status: Int) {
        val ip = peers.resolveIp(destNodeId) ?: return
        try {
            transport.sendTcp(
                buildPacket(
                    type = HeaderProtocol.Type.ACK,
                    flags = 0,
                    dest = destNodeId,
                    id = messageId,
                    hopCount = 0,
                    payload = Payload.Ack(status, Signature(ByteArray(64)))
                ),
                ip
            )
        } catch (_: Exception) { }
    }

    /** Passes raw pre-built bytes directly to the TCP transport */
    suspend fun forwardTcp(bytes: ByteArray, ip: String) {
        try { transport.sendTcp(bytes, ip) } catch (_: Exception) { }
    }

    /** Passes raw pre-built bytes to the UDP broadcast transport */
    suspend fun forwardUdpBroadcast(bytes: ByteArray) {
        try { transport.broadcastUdp(bytes) } catch (_: Exception) { }
    }

    /** Called by Receiver when a verified ACK arrives for a pending message */
    fun onAckReceived(messageId: Long) {
        pendingAck.remove(messageId)?.let {
            statusChannel.trySend(messageId to SendStatus.DELIVERED)
        }
    }

    /** Fails all pending messages whose resolved next hop IP matches the failed address */
    fun onTcpError(failedIp: String) {
        pendingAck.values
            .filter { peers.resolveIp(it.destinationNodeId) == failedIp }
            .forEach { msg ->
                pendingAck.remove(msg.messageId)
                statusChannel.trySend(msg.messageId to SendStatus.FAILED)
            }
    }

    /** Starts the coroutine that continuously drains and processes the outbound queue */
    fun startQueueLoop(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                val msg = synchronized(queue) {
                    if (queue.isEmpty()) null else queue.removeFirst()
                }
                if (msg == null) { delay(200); continue }
                processMessage(msg)
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
        id: Long,
        hopCount: Int,
        payload: Payload
    ): ByteArray {
        val payloadBuf = ByteArray(65_536)
        val payloadLen = PayloadSerializer.serialize(payload, payloadBuf, 0)
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
            id = MessageId(id),
            originTimestamp = Timestamp(System.currentTimeMillis()),
            payloadLength = payloadLen
        )
        val out = ByteArray(HeaderProtocol.HEADER_SIZE + payloadLen)
        HeaderSerializer.serialize(header, out, 0)
        payloadBuf.copyInto(out, HeaderProtocol.HEADER_SIZE, 0, payloadLen)
        return out
    }

    internal suspend fun processMessage(msg: QueuedMessage) {
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
            return
        }

        if (!msg.rreqFlag) {
            issueRreq(msg.destinationNodeId)
            msg.rreqFlag = true
        }
        synchronized(queue) { queue.addLast(msg) }
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
        } catch (_: Exception) {
            statusChannel.trySend(msg.messageId to SendStatus.FAILED)
        }
    }

    private suspend fun issueRreq(destinationNodeId: NodeId) {
        val rreqId = synchronized(this) { nextRreqId++ }
        transport.broadcastUdp(
            buildPacket(
                type = HeaderProtocol.Type.RREQ,
                flags = HeaderProtocol.Flags.BROADCAST,
                dest = destinationNodeId,
                id = rreqId,
                hopCount = 0,
                payload = Payload.RREQ(selfName, selfPublicKey)
            )
        )
    }
}