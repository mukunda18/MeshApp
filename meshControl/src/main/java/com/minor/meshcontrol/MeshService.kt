package com.minor.meshcontrol

import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.NodesStore
import com.minor.model.PacketSigner
import com.minor.model.PacketVerifier
import com.minor.model.Payload
import com.minor.network.MeshTransport
import com.minor.routing.PeerEvent
import com.minor.routing.RoutingModule
import com.minor.routing.SendStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground-owned mesh lifecycle coordinator.
 *
 * MeshService intentionally has no Android dependency. Android-specific code should
 * implement MeshSocketFactory to create Context-bound sockets.
 */
class MeshService(
    private val config: MeshConfig,
    private val socketFactory: MeshSocketFactory,
    private val nodesStore: NodesStore,
    private val signer: PacketSigner? = null,
    private val verifier: PacketVerifier? = null
) {
    private val mutex = Mutex()
    private var routingModule: RoutingModule? = null
    private var sockets: MeshSockets? = null
    private var serviceScope: CoroutineScope? = null

    private val _deliveryStatusStream = MutableSharedFlow<DeliveryStatus>(extraBufferCapacity = 64)
    val deliveryStatusStream: SharedFlow<DeliveryStatus> = _deliveryStatusStream.asSharedFlow()

    private val _peerEventsStream = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 64)
    val peerEventsStream: SharedFlow<PeerEvent> = _peerEventsStream.asSharedFlow()

    private val _incomingMessageStream = MutableSharedFlow<Pair<NodeId, Payload.Message>>(extraBufferCapacity = 64)
    val incomingMessageStream: SharedFlow<Pair<NodeId, Payload.Message>> = _incomingMessageStream.asSharedFlow()

    suspend fun start() = mutex.withLock {
        if (serviceScope != null) return@withLock // Already running

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        serviceScope = scope

        // 1. Create Sockets using Factory
        val newSockets = socketFactory.create(scope, config)
        sockets = newSockets

        val transport = MeshTransport(newSockets.tcpSender, newSockets.udpSocket)

        val rm = RoutingModule(
            selfNodeId = config.ownNodeId,
            selfPublicKey = config.ownPublicKey,
            selfName = config.ownName,
            transport = transport,
            tcpIncoming = newSockets.tcpReceiver.incoming,
            udpIncoming = newSockets.udpSocket.incoming,
            nodesStore = nodesStore,
            signer = signer,
            verifier = verifier,
            rreqRetryTimeoutMs = config.rreqRetryTimeoutMs,
            maxHopCount = config.maxHopCount,
            freshnessWindowMs = config.originTimestampFreshnessWindowMs
        ).also { routingModule = it }

        // 3. Start Routing Logic
        rm.start(scope = scope, displayName = config.ownName)

        // 4. Start Transport Receiving
        newSockets.tcpReceiver.start()
        newSockets.udpSocket.start()

        // 5. Bridge Internal Channels to Flows
        scope.launch {
            for (statusUpdate in rm.sender.statusChannel) {
                _deliveryStatusStream.emit(DeliveryStatus(statusUpdate.first, statusUpdate.second.toDeliveryState()))
            }
        }

        scope.launch {
            for (event in rm.peers.peerEvents) {
                _peerEventsStream.emit(event)
            }
        }

        scope.launch {
            for (pair in rm.receiver.incomingPayloadChannel) {
                _incomingMessageStream.emit(pair)
            }
        }
    }

    suspend fun stop() = mutex.withLock {
        serviceScope?.cancel()
        serviceScope = null

        routingModule?.stop()
        routingModule = null

        // Clean close of sockets
        sockets?.let {
            it.tcpReceiver.close()
            it.udpSocket.close()
            it.tcpSender.close()
        }
        sockets = null
    }

    fun sendMessage(destinationNodeID: NodeId, payload: Payload.Message, messageId: MessageId) {
        val rm = routingModule ?: error("MeshService not running")
        rm.sender.enqueue(messageId, payload, destinationNodeID)
    }

    fun getRoutes() = routingModule?.router?.getRoutes() ?: emptyList()
    fun getPeers() = routingModule?.peers?.getPeers() ?: emptyList()

    private fun SendStatus.toDeliveryState() = when (this) {
        SendStatus.SENT -> DeliveryState.SENT
        SendStatus.DELIVERED -> DeliveryState.DELIVERED
        SendStatus.FAILED -> DeliveryState.FAILED
    }
}
