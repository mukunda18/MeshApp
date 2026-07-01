package com.minor.routing

import kotlinx.coroutines.CoroutineScope

/** Wires the four routing components together for a single node */
class RoutingModule(
    selfNodeID: String,
    transport: TransportLink,
    builder: PacketBuilder
) {
    val router = Router()
    val sender: Sender
    val peers: PeersManagement
    val receiver: Receiver

    init {
        sender = Sender(selfNodeID, transport, builder, router, object : PeersManagementLookup {
            override fun resolveIp(nodeID: String): String? = peers.resolveIp(nodeID)
            override fun isDirectPeer(nodeID: String): Boolean = peers.isDirectPeer(nodeID)
        })
        peers = PeersManagement(selfNodeID, router, sender)
        receiver = Receiver(selfNodeID, router, peers, sender, transport, builder)
    }

    /** Starts all background loops owned by the routing module */
    fun start(scope: CoroutineScope, displayName: String) {
        router.startExpiryLoop(scope)
        peers.startReaperLoop(scope)
        peers.startHelloBroadcastLoop(scope, displayName)
        sender.startQueueLoop(scope)
    }
}