package com.minor.routing

import com.minor.model.Envelope
import com.minor.model.NodeId
import com.minor.model.NodesStore
import com.minor.model.PacketSigner
import com.minor.model.PacketVerifier
import com.minor.model.PublicKey
import com.minor.network.MeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/**
 * Wires Router, PeersManagement, Sender, and Receiver into one cohesive lifecycle unit.
 */
class RoutingModule(
    val selfNodeId: NodeId,
    val selfPublicKey: PublicKey,
    val selfName: String,
    val transport: MeshTransport,
    private val tcpIncoming: ReceiveChannel<Envelope>,
    private val udpIncoming: ReceiveChannel<Envelope>,
    private val nodesStore: NodesStore,
    private val signer: PacketSigner? = null,
    private val verifier: PacketVerifier? = null,
    rreqRetryTimeoutMs: Long,
    maxHopCount: Int,
    freshnessWindowMs: Long
) {
    val router = Router()
    val peers = PeersManagement(selfNodeId, router)

    val sender = Sender(
        selfNodeId = selfNodeId,
        selfPublicKey = selfPublicKey,
        selfName = selfName,
        transport = transport,
        router = router,
        peers = peers,
        nodesStore = nodesStore,
        signer = signer,
        rreqRetryTimeoutMs = rreqRetryTimeoutMs,
        maxHopCount = maxHopCount
    )

    val receiver = Receiver(
        selfNodeId = selfNodeId,
        router = router,
        peers = peers,
        sender = sender,
        nodesStore = nodesStore,
        verifier = verifier,
        freshnessWindowMs = freshnessWindowMs
    )

    private var activeJob: Job? = null

    /** Starts all background loops and begins collecting from both inbound channels */
    fun start(scope: CoroutineScope, displayName: String) {
        if (activeJob != null) return

        activeJob = scope.launch {
            sender.startQueueLoop(this)
            peers.startReaperLoop(this, sender)
            peers.startHelloBroadcastLoop(this, sender, displayName)

            launch {
                for (envelope in tcpIncoming) {
                    val ip = envelope.remoteAddress.address.hostAddress ?: continue
                    receiver.onPacketReceived(envelope.packet, ip)
                }
            }

            launch {
                for (envelope in udpIncoming) {
                    val ip = envelope.remoteAddress.address.hostAddress ?: continue
                    receiver.onPacketReceived(envelope.packet, ip)
                }
            }
        }
    }

    /** Safely stops all background loops */
    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }
}
