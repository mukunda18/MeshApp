package com.minor.routing

import com.minor.model.NodeId
import com.minor.model.PublicKey
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val peerEvents = Channel<PeerEvent>(capacity = Channel.UNLIMITED)

    /** Adds a new peer or refreshes the IP name and lastSeen of an existing one */
    fun addOrUpdate(nodeId: NodeId, ip: String, name: String, publicKey: PublicKey) {
        val now = System.currentTimeMillis()
        val peer = Peer(nodeId, ip, name, publicKey, now)
        val existed = peers.put(nodeId.toString(), peer) != null
        peerEvents.trySend(if (existed) PeerEvent.Updated(peer) else PeerEvent.Added(peer))
    }

    /** Removes a peer explicitly and emits a Removed event */
    fun remove(nodeId: NodeId) {
        if (peers.remove(nodeId.toString()) != null) {
            peerEvents.trySend(PeerEvent.Removed(nodeId))
        }
    }

    /** Returns the current IP address for a known peer or null */
    fun resolveIp(nodeId: NodeId): String? = peers[nodeId.toString()]?.ip

    /** Returns true when the node is currently in the direct peer table */
    fun isDirectPeer(nodeId: NodeId): Boolean = peers.containsKey(nodeId.toString())

    /** Returns the stored public key for a peer or null */
    fun lookupPublicKey(nodeId: NodeId): PublicKey? = peers[nodeId.toString()]?.publicKey

    /** Returns a point in time snapshot of all known peers */
    fun getPeers(): List<Peer> = peers.values.toList()

    /** Checks that SHA256 of the public key bytes equals the claimed NodeId bytes */
    fun verifyNodeId(publicKey: PublicKey, claimedNodeId: NodeId): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.bytes)
        return digest.contentEquals(claimedNodeId.bytes)
    }

    /**
     * Removes peers whose lastSeen exceeds peerTimeoutMs
     * Calls Router invalidateVia for each removed peer and broadcasts RERR for affected routes
     */
    fun startReaperLoop(scope: CoroutineScope, sender: Sender) {
        scope.launch {
            while (true) {
                delay(reaperCheckMs)
                val now = System.currentTimeMillis()
                val timedOut = peers.values.filter { now - it.lastSeen > peerTimeoutMs }
                for (peer in timedOut) {
                    peers.remove(peer.nodeId.toString())
                    peerEvents.trySend(PeerEvent.Removed(peer.nodeId))
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
                delay(helloIntervalMs)
            }
        }
    }
}