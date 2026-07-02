package com.minor.routing

import com.minor.model.NodeId
import com.minor.model.Packet
import com.minor.model.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Wires Router PeersManagement Sender and Receiver into one cohesive lifecycle unit
 *
 * IMPORTANT NOTE ON SENDER IP
 * TCPReceiver and UdpSocket currently deliver packets without the sender IP address
 * MeshControl must be extended to extract the source IP at the transport layer and
 * send Packet plus IP pairs into inboundPackets before calling start
 * Until that is done HELLO and RREQ peer registration will not function correctly
 */
class RoutingModule(
    selfNodeId: NodeId,
    selfPublicKey: PublicKey,
    selfName: String,
    transport: MeshTransport
) {
    val router = Router()
    val peers: PeersManagement
    val sender: Sender
    val receiver: Receiver

    /**
     * Feed every inbound packet from TCPReceiver and UdpSocket into this channel
     * paired with the IP address of the node that sent it
     */
    val inboundPackets = Channel<Pair<Packet, String>>(capacity = Channel.UNLIMITED)

    init {
        peers = PeersManagement(selfNodeId, router)
        sender = Sender(selfNodeId, selfPublicKey, selfName, transport, router, peers)
        receiver = Receiver(selfNodeId, router, peers, sender)
    }

    /** Starts all background loops and begins draining inboundPackets */
    fun start(scope: CoroutineScope, displayName: String) {
        router.startExpiryLoop(scope)
        sender.startQueueLoop(scope)
        peers.startReaperLoop(scope, sender)
        peers.startHelloBroadcastLoop(scope, sender, displayName)
        scope.launch {
            for ((packet, senderIp) in inboundPackets) {
                receiver.onPacketReceived(packet, senderIp)
            }
        }
    }

    /** Stops the inbound channel so the dispatch loop exits cleanly */
    fun stop() {
        inboundPackets.close()
    }
}