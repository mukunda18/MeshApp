package com.minor.routing

import com.minor.model.NodeId
import com.minor.model.PublicKey
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stores one Peer record per direct neighbour and emits PeerEvents on changes
 * Two background loops run via start methods: a reaper that times out stale peers
 * and a broadcaster that periodically sends this nodes HELLO to all neighbours
 */
class PeersManagement(
    private val selfNodeId: NodeId,
    private val router: Router,
    private val peerTimeoutMs: Long = 15_000,
    private val reaperCheckMs: Long = 5_000,
    private val helloIntervalMs: Long = 5_000
) {
    private val peers = ConcurrentHashMap<String, Peer>()

    /** Collectable stream of peer table mutations for upper layer observation */
    val peerEventsChannel = Channel<PeerEvent>(capacity = Channel.UNLIMITED)
    val peerEvents: ReceiveChannel<PeerEvent> get() = peerEventsChannel

    /** Adds a new peer or refreshes the IP and lastSeen of an existing one */
    fun addOrUpdate(nodeId: NodeId, ip: String) {
        val now = System.currentTimeMillis()
        val peer = Peer(nodeId, ip, now)
        val existed = peers.put(nodeId.toString(), peer) != null
        peerEventsChannel.trySend(if (existed) PeerEvent.Updated(peer) else PeerEvent.Added(peer))
    }

    /** Removes a peer explicitly and emits a Removed event */
    fun remove(nodeId: NodeId) {
        if (peers.remove(nodeId.toString()) != null) {
            peerEventsChannel.trySend(PeerEvent.Removed(nodeId))
        }
    }

    /** Returns the current IP address for a known peer or null */
    fun resolveIp(nodeId: NodeId): String? = peers[nodeId.toString()]?.ip

    /** Returns true when the node is currently in the direct peer table */
    fun isDirectPeer(nodeId: NodeId): Boolean = peers.containsKey(nodeId.toString())

    /** Returns a point in time snapshot of all known peers */
    fun getPeers(): List<Peer> = peers.values.toList()

    /**
     * Removes peers whose lastSeen exceeds peerTimeoutMs
     * Calls Router invalidateVia for each removed peer and broadcasts RERR for affected routes
     */
    fun startReaperLoop(scope: CoroutineScope, sender: Sender) {
        scope.launch {
            while (true) {
                delay(reaperCheckMs.milliseconds)
                val now = System.currentTimeMillis()
                val timedOut = peers.values.filter { now - it.lastSeen > peerTimeoutMs }
                for (peer in timedOut) {
                    peers.remove(peer.nodeId.toString())
                    peerEventsChannel.trySend(PeerEvent.Removed(peer.nodeId))
                    val affected = router.invalidateVia(peer.nodeId)
                    if (affected.isNotEmpty()) sender.broadcastRerr(affected)
                }
            }
        }
    }

    /** Calls broadcastHello on the given Sender every helloIntervalMs milliseconds */
    fun startHelloBroadcastLoop(scope: CoroutineScope, sender: Sender, displayName: String) {
        scope.launch {
            while (true) {
                sender.broadcastHello(displayName)
                delay(helloIntervalMs.milliseconds)
            }
        }
    }
}