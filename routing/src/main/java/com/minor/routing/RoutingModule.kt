package com.minor.routing

import com.minor.model.Envelope
import com.minor.model.NodeId
import com.minor.model.PublicKey
import com.minor.network.MeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/**
 * Wires Router PeersManagement Sender and Receiver into one cohesive lifecycle unit
 * Accepts the two Envelope channels from TCPReceiver and UdpSocket directly
 * Each Envelope carries both the parsed Packet and the remoteAddress of the sender
 * so peer registration and RREQ session routing work correctly without any extra bridging
 */
class RoutingModule(
    selfNodeId: NodeId,
    selfPublicKey: PublicKey,
    selfName: String,
    transport: MeshTransport,
    private val tcpIncoming: ReceiveChannel<Envelope>,
    private val udpIncoming: ReceiveChannel<Envelope>
) {
    val router = Router()
    val peers: PeersManagement = PeersManagement(selfNodeId, router)
    val sender: Sender = Sender(selfNodeId, selfPublicKey, selfName, transport, router, peers)
    val receiver: Receiver = Receiver(selfNodeId, router, peers, sender)

    /** Starts all background loops and begins collecting from both inbound channels */
    fun start(scope: CoroutineScope, displayName: String) {
        router.startExpiryLoop(scope)
        sender.startQueueLoop(scope)
        peers.startReaperLoop(scope, sender)
        peers.startHelloBroadcastLoop(scope, sender, displayName)

        scope.launch {
            for (envelope in tcpIncoming) {
                val ip = envelope.remoteAddress.address.hostAddress ?: continue
                receiver.onPacketReceived(envelope.packet, ip)
            }
        }

        scope.launch {
            for (envelope in udpIncoming) {
                val ip = envelope.remoteAddress.address.hostAddress ?: continue
                receiver.onPacketReceived(envelope.packet, ip)
            }
        }
    }
}