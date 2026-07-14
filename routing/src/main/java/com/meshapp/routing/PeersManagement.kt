package com.meshapp.routing

import com.meshapp.model.NodeId
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
    private val peerTimeoutMs: Long,
    private val reaperCheckMs: Long,
    private val helloIntervalMs: Long
) {
    private val peers = ConcurrentHashMap<NodeId, Peer>()

    /** Collectable stream of peer table mutations for upper layer observation */
    val peerEventsChannel = Channel<PeerEvent>(capacity = Channel.UNLIMITED)
    val peerEvents: ReceiveChannel<PeerEvent> get() = peerEventsChannel

    /** Adds a new peer or refreshes the IP and lastSeen of an existing one */
    fun addOrUpdate(nodeId: NodeId, ip: String) {
        if (nodeId == selfNodeId) return

        val now = System.currentTimeMillis()
        val peer = Peer(nodeId, ip, now)
        val existed = peers.put(nodeId, peer) != null
        peerEventsChannel.trySend(if (existed) PeerEvent.Updated(peer) else PeerEvent.Added(peer))
    }

    /** Removes a peer explicitly and emits a Removed event */
    fun remove(nodeId: NodeId) {
        if (peers.remove(nodeId) != null) {
            peerEventsChannel.trySend(PeerEvent.Removed(nodeId))
        }
    }

    /** Returns the current IP address for a known peer or null */
    fun resolveIp(nodeId: NodeId): String? = peers[nodeId]?.ip

    /** Returns true when the node is currently in the direct peer table */
    fun isDirectPeer(nodeId: NodeId): Boolean = peers.containsKey(nodeId)

    /** Returns a point in time snapshot of all known peers */
    fun getPeers(): List<Peer> = peers.values.toList()

    /**
     * Removes peers whose lastSeen exceeds peerTimeoutMs
     * Calls Router invalidateVia for each removed peer and broadcasts RERR for affected routes
     */
    fun startReaperLoop(scope: CoroutineScope, sender: Sender) {
        scope.launch {
            while (true) {
                try {
                    delay(reaperCheckMs.milliseconds)
                    val now = System.currentTimeMillis()
                    val timedOut = peers.values.filter { now - it.lastSeen > peerTimeoutMs }
                    
                    if (timedOut.isNotEmpty()) {
                        val allAffected = mutableSetOf<NodeId>()
                        for (peer in timedOut) {
                            peers.remove(peer.nodeId)
                            peerEventsChannel.trySend(PeerEvent.Removed(peer.nodeId))
                            allAffected.addAll(router.invalidateVia(peer.nodeId))
                        }
                        
                        if (allAffected.isNotEmpty()) {
                            sender.broadcastRerr(allAffected.toList())
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("PeersManagement", "Error in reaper loop", e)
                }
            }
        }
    }

    /** Calls broadcastHello on the given Sender every helloIntervalMs milliseconds */
    fun startHelloBroadcastLoop(scope: CoroutineScope, sender: Sender, displayName: String) {
        scope.launch {
            while (true) {
                try {
                    sender.broadcastHello(displayName)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("PeersManagement", "Error in hello loop", e)
                }
                delay(helloIntervalMs.milliseconds)
            }
        }
    }
}