package com.minor.routing

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Status of an outbound message lifecycle */
enum class SendStatus { SENT, DELIVERED, FAILED }

data class QueuedMessage(
    val messageID: Long,
    val payload: ByteArray,
    val destinationNodeID: String,
    var rreqFlag: Boolean = false,
    val enqueueTime: Long = System.currentTimeMillis()
)

/**
 * Sends messages toward a destination using direct delivery known routes or RREQ discovery
 * Drains a queue on a single background loop and reports delivery status
 */
class Sender(
    private val selfNodeID: String,
    private val transport: TransportLink,
    private val builder: PacketBuilder,
    private val router: Router,
    private val peers: PeersManagementLookup,
    private val rreqRetryTimeoutMs: Long = 8_000,
    private val ackTimeoutMs: Long = 8_000
) {

    private val queue = ArrayDeque<QueuedMessage>()
    private val pendingAck = ConcurrentHashMap<Long, QueuedMessage>()
    private var nextRreqID = 1L

    val statusChannel = Channel<Pair<Long, SendStatus>>(capacity = Channel.UNLIMITED)

    /** Enqueues a message to be sent toward the given destination */
    fun sendMessage(messageID: Long, payload: ByteArray, destinationNodeID: String) {
        synchronized(queue) {
            queue.addLast(QueuedMessage(messageID, payload, destinationNodeID))
        }
    }

    /** Broadcasts a HELLO packet carrying the current route table snapshot */
    fun broadcastHello(selfNodeID: String, name: String, routes: List<RouteEntry>) {
        val packet = builder.buildHello(selfNodeID, name, routes)
        transport.sendUdpBroadcast(builder.serialize(packet))
    }

    /** Broadcasts a RERR packet for the given unreachable destinations */
    fun broadcastRerr(unreachable: List<String>) {
        val packet = builder.buildRerr(unreachable, selfNodeID)
        transport.sendUdpBroadcast(builder.serialize(packet))
    }

    /** Called by the Receiver when a verified ACK arrives for a queued message */
    fun onAckReceived(messageID: Long) {
        val msg = pendingAck.remove(messageID)
        if (msg != null) {
            statusChannel.trySend(messageID to SendStatus.DELIVERED)
        }
    }

    /** Called by the transport layer when a TCP send fails for a given address */
    fun onTcpError(failedAddress: String) {
        val failed = pendingAck.values.filter { peers.resolveIp(it.destinationNodeID) == failedAddress }
        for (msg in failed) {
            pendingAck.remove(msg.messageID)
            statusChannel.trySend(msg.messageID to SendStatus.FAILED)
        }
    }

    /** Starts the queue processing loop that drains and dispatches messages */
    fun startQueueLoop(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                val msg = synchronized(queue) { if (queue.isEmpty()) null else queue.removeFirst() }
                if (msg == null) {
                    delay(200)
                    continue
                }
                processMessage(msg)
            }
        }
    }

    private fun processMessage(msg: QueuedMessage) {
        val now = System.currentTimeMillis()
        if (now - msg.enqueueTime > rreqRetryTimeoutMs && router.lookup(msg.destinationNodeID) == null
            && !peers.isDirectPeer(msg.destinationNodeID)
        ) {
            statusChannel.trySend(msg.messageID to SendStatus.FAILED)
            return
        }

        if (peers.isDirectPeer(msg.destinationNodeID)) {
            val ip = peers.resolveIp(msg.destinationNodeID)
            if (ip != null) {
                deliver(msg, ip)
                return
            }
        }

        val nextHop = router.lookup(msg.destinationNodeID)
        if (nextHop != null) {
            val ip = peers.resolveIp(nextHop)
            if (ip != null) {
                deliver(msg, ip)
                return
            }
        }

        if (!msg.rreqFlag) {
            val rreqID = nextRreqID++
            val packet = builder.buildRREQ(rreqID, msg.destinationNodeID)
            transport.sendUdpBroadcast(builder.serialize(packet))
            msg.rreqFlag = true
        }
        synchronized(queue) { queue.addLast(msg) }
    }

    private fun deliver(msg: QueuedMessage, ip: String) {
        val packet = builder.buildMsg(msg.payload, msg.messageID, msg.destinationNodeID)
        transport.sendTcp(builder.serialize(packet), ip)
        pendingAck[msg.messageID] = msg
        statusChannel.trySend(msg.messageID to SendStatus.SENT)
    }
}

/** Narrow lookup contract the Sender needs from PeersManagement */
interface PeersManagementLookup {
    fun resolveIp(nodeID: String): String?
    fun isDirectPeer(nodeID: String): Boolean
}