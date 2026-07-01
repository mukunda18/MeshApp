package com.minor.routing

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stores direct peers seen via HELLO packets and reaps timed out peers
 * Emits peer table changes on an internal peerEvents channel
 */
class PeersManagement(
    private val selfNodeID: String,
    private val router: Router,
    private val sender: Sender,
    private val peerTimeoutMs: Long = 15_000,
    private val reaperIntervalMs: Long = 5_000,
    private val helloIntervalMs: Long = 5_000,
    private val maxHopCount: Int = 8
) : PeersManagementLookup {

    private val peers = ConcurrentHashMap<String, Peer>()

    val peerEvents = Channel<PeerEvent>(capacity = Channel.UNLIMITED)

    /** Adds a new peer or refreshes lastSeen and address for an existing one */
    fun addOrUpdate(nodeID: String, ip: String, name: String) {
        val now = System.currentTimeMillis()
        val existing = peers[nodeID]
        val peer = Peer(nodeID, ip, name, now)
        peers[nodeID] = peer
        if (existing == null) {
            peerEvents.trySend(PeerEvent.Added(peer))
        } else {
            peerEvents.trySend(PeerEvent.Updated(peer))
        }
    }

    /** Removes a peer explicitly */
    fun remove(nodeID: String) {
        if (peers.remove(nodeID) != null) {
            peerEvents.trySend(PeerEvent.Removed(nodeID))
        }
    }

    /** Resolves the IP address for a given peer */
    override fun resolveIp(nodeID: String): String? = peers[nodeID]?.ip

    /** Returns whether a node is currently a direct neighbour */
    override fun isDirectPeer(nodeID: String): Boolean = peers.containsKey(nodeID)

    /** Returns a snapshot of all currently known peers */
    fun getPeers(): List<Peer> = peers.values.toList()

    /** Starts the loop that removes peers which have not sent a HELLO in time */
    fun startReaperLoop(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                delay(reaperIntervalMs)
                val now = System.currentTimeMillis()
                val timedOut = peers.values.filter { now - it.lastSeen > peerTimeoutMs }
                for (peer in timedOut) {
                    peers.remove(peer.nodeID)
                    peerEvents.trySend(PeerEvent.Removed(peer.nodeID))
                    val affected = router.invalidateVia(peer.nodeID)
                    if (affected.isNotEmpty()) {
                        sender.broadcastRerr(affected)
                    }
                }
            }
        }
    }

    /** Starts the loop that periodically broadcasts this node's HELLO */
    fun startHelloBroadcastLoop(scope: CoroutineScope, displayName: String) {
        scope.launch {
            while (true) {
                val routeEntries = router.getRoutes()
                    .filter { it.hopCount <= maxHopCount - 1 }
                    .map { RouteEntry(it.destinationNodeID, it.hopCount, "") }
                sender.broadcastHello(selfNodeID, displayName, routeEntries)
                delay(helloIntervalMs)
            }
        }
    }
}