package com.minor.meshcontrol

import com.minor.model.NodeId
import com.minor.model.Packet
import com.minor.model.Payload
import com.minor.model.PublicKey
import com.minor.network.TCPReceiver
import com.minor.network.TCPSender
import com.minor.network.UdpSocket
import com.minor.routing.MeshTransport
import com.minor.routing.Peer
import com.minor.routing.PeerEvent
import com.minor.routing.PeersManagement
import com.minor.routing.RealMeshTransport
import com.minor.routing.Receiver
import com.minor.routing.RouteInfo
import com.minor.routing.Router
import com.minor.routing.SendStatus
import com.minor.routing.Sender
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MeshSockets(
    val tcpReceiver: TCPReceiver,
    val tcpSender: TCPSender,
    val udpSocket: UdpSocket
)

fun interface MeshSocketFactory {
    fun create(scope: CoroutineScope, config: MeshConfig): MeshSockets
}

enum class MeshState {
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR
}

enum class DeliveryState {
    SENT,
    DELIVERED,
    FAILED
}

data class DeliveryStatus(
    val messageId: Long,
    val state: DeliveryState
)

enum class PeerStatus {
    ACTIVE,
    REMOVED
}

data class PeerState(
    val nodeId: NodeId,
    val ip: String?,
    val name: String?,
    val publicKey: PublicKey?,
    val status: PeerStatus,
    val lastSeen: Long?
)

data class RouteState(
    val routes: List<RouteInfo>
)

interface MeshMessagingGateway {
    val incomingMessageStream: SharedFlow<Packet>
    val deliveryStatusStream: SharedFlow<DeliveryStatus>

    fun sendMessage(destinationNodeID: NodeId, payload: Payload): Long
}

/**
 * Foreground-owned mesh lifecycle coordinator.
 *
 * MeshService intentionally has no Android dependency. Android-specific code should
 * create Context-bound sockets inside MeshSocketFactory, then pass them here.
 */
class MeshService(
    private val config: MeshConfig,
    private val socketFactory: MeshSocketFactory,
    private val scopeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : MeshMessagingGateway {
    private val nextMessageId = AtomicLong(1L)

    private val _meshStateStream = MutableStateFlow(MeshState.STOPPED)
    val meshStateStream: StateFlow<MeshState> = _meshStateStream.asStateFlow()

    private val _incomingMessageStream = MutableSharedFlow<Packet>(
        extraBufferCapacity = STREAM_BUFFER_CAPACITY
    )
    override val incomingMessageStream: SharedFlow<Packet> = _incomingMessageStream.asSharedFlow()

    private val _deliveryStatusStream = MutableSharedFlow<DeliveryStatus>(
        extraBufferCapacity = STREAM_BUFFER_CAPACITY
    )
    override val deliveryStatusStream: SharedFlow<DeliveryStatus> = _deliveryStatusStream.asSharedFlow()

    private val _peersStream = MutableStateFlow<List<PeerState>>(emptyList())
    val peersStream: StateFlow<List<PeerState>> = _peersStream.asStateFlow()

    private val _routeStateStream = MutableStateFlow(RouteState(emptyList()))
    val routeStateStream: StateFlow<RouteState> = _routeStateStream.asStateFlow()

    private var lifecycleJob: Job? = null
    private var sockets: MeshSockets? = null
    private var router: Router? = null
    private var peers: PeersManagement? = null
    private var sender: Sender? = null
    private var receiver: Receiver? = null

    @Synchronized
    fun start() {
        if (lifecycleJob?.isActive == true) return

        _meshStateStream.value = MeshState.STARTING
        val job = SupervisorJob()
        val scope = CoroutineScope(job + scopeDispatcher)
        lifecycleJob = job

        try {
            val createdSockets = socketFactory.create(scope, config)
            val observableTransport = ObservableMeshTransport(
                delegate = RealMeshTransport(createdSockets.tcpSender, createdSockets.udpSocket)
            )
            val createdRouter = Router(
                routeExpiryMs = config.routeExpiryMs,
                expiryCheckIntervalMs = config.routeExpiryCheckIntervalMs
            )
            val createdPeers = PeersManagement(
                selfNodeId = config.ownNodeId,
                router = createdRouter,
                peerTimeoutMs = config.peerTimeoutMs,
                reaperCheckMs = config.peerReaperCheckMs,
                helloIntervalMs = config.helloIntervalMs
            )
            val createdSender = Sender(
                selfNodeId = config.ownNodeId,
                selfPublicKey = config.ownPublicKey,
                selfName = config.ownName,
                transport = observableTransport,
                router = createdRouter,
                peers = createdPeers,
                rreqRetryTimeoutMs = config.rreqRetryTimeoutMs,
                maxHopCount = config.maxHopCount
            )
            val createdReceiver = Receiver(
                selfNodeId = config.ownNodeId,
                router = createdRouter,
                peers = createdPeers,
                sender = createdSender,
                freshnessWindowMs = config.originTimestampFreshnessWindowMs
            )

            sockets = createdSockets
            router = createdRouter
            peers = createdPeers
            sender = createdSender
            receiver = createdReceiver

            createdSockets.tcpReceiver.start()
            createdSockets.udpSocket.start()
            createdSender.startQueueLoop(scope)
            createdPeers.startHelloBroadcastLoop(scope, createdSender, config.ownName)
            createdPeers.startReaperLoop(scope, createdSender)
            createdRouter.startExpiryLoop(scope)

            startTransportCollectors(scope, createdSockets, createdReceiver)
            startInternalAggregators(
                scope = scope,
                peers = createdPeers,
                router = createdRouter,
                sender = createdSender,
                receiver = createdReceiver,
                tcpErrors = observableTransport.tcpErrors
            )

            _meshStateStream.value = MeshState.RUNNING
        } catch (error: Throwable) {
            _meshStateStream.value = MeshState.ERROR
            scope.launch { stopInternal() }
            if (error is RuntimeException) throw error
            throw IllegalStateException("Unable to start mesh service", error)
        }
    }

    override fun sendMessage(destinationNodeID: NodeId, payload: Payload): Long {
        val activeSender = sender ?: error("MeshService must be running before sending messages")
        val messageId = nextMessageId.getAndIncrement()
        activeSender.enqueue(messageId, payload, destinationNodeID)
        return messageId
    }

    suspend fun stop() {
        stopInternal()
    }

    private fun startTransportCollectors(
        scope: CoroutineScope,
        sockets: MeshSockets,
        receiver: Receiver
    ) {
        scope.launch {
            for (envelope in sockets.tcpReceiver.incoming) {
                receiver.onPacketReceived(
                    packet = envelope.packet,
                    senderIp = envelope.remoteAddress.address.hostAddress
                )
            }
        }
        scope.launch {
            for (envelope in sockets.udpSocket.incoming) {
                receiver.onPacketReceived(
                    packet = envelope.packet,
                    senderIp = envelope.remoteAddress.address.hostAddress
                )
            }
        }
    }

    private fun startInternalAggregators(
        scope: CoroutineScope,
        peers: PeersManagement,
        router: Router,
        sender: Sender,
        receiver: Receiver,
        tcpErrors: Channel<String>
    ) {
        scope.launch {
            for (packet in receiver.incomingMessageChannel) {
                _incomingMessageStream.emit(packet)
            }
        }
        scope.launch {
            for ((messageId, status) in sender.statusChannel) {
                _deliveryStatusStream.emit(DeliveryStatus(messageId, status.toDeliveryState()))
            }
        }
        scope.launch {
            for (event in peers.peerEvents) {
                _peersStream.update { current -> current.applyPeerEvent(event) }
            }
        }
        scope.launch {
            for (failedIp in tcpErrors) {
                sender.onTcpError(failedIp)
            }
        }
        scope.launch {
            while (true) {
                _routeStateStream.value = RouteState(router.getRoutes())
                kotlinx.coroutines.delay(config.routeStateIntervalMs)
            }
        }
    }

    private suspend fun stopInternal() {
        if (lifecycleJob == null && _meshStateStream.value == MeshState.STOPPED) return

        _meshStateStream.value = MeshState.STOPPING
        lifecycleJob?.cancel()

        val activeSockets = sockets

        try {
            withContext(NonCancellable) {
                activeSockets?.tcpReceiver?.close()
                activeSockets?.udpSocket?.close()
                activeSockets?.tcpSender?.close()
            }
        } catch (_: CancellationException) {
            throw
        } catch (_: Throwable) {
            _meshStateStream.value = MeshState.ERROR
        } finally {
            _peersStream.value = emptyList()
            _routeStateStream.value = RouteState(emptyList())
            sockets = null
            router = null
            peers = null
            sender = null
            receiver = null
            lifecycleJob = null
            if (_meshStateStream.value != MeshState.ERROR) {
                _meshStateStream.value = MeshState.STOPPED
            }
        }
    }

    private fun SendStatus.toDeliveryState(): DeliveryState = when (this) {
        SendStatus.SENT -> DeliveryState.SENT
        SendStatus.DELIVERED -> DeliveryState.DELIVERED
        SendStatus.FAILED -> DeliveryState.FAILED
    }

    private fun List<PeerState>.applyPeerEvent(event: PeerEvent): List<PeerState> {
        val byNode = associateBy { it.nodeId.toString() }.toMutableMap()
        when (event) {
            is PeerEvent.Added -> byNode[event.peer.nodeId.toString()] = event.peer.toPeerState()
            is PeerEvent.Updated -> byNode[event.peer.nodeId.toString()] = event.peer.toPeerState()
            is PeerEvent.Removed -> {
                val existing = byNode[event.nodeId.toString()]
                byNode[event.nodeId.toString()] = PeerState(
                    nodeId = event.nodeId,
                    ip = existing?.ip,
                    name = existing?.name,
                    publicKey = existing?.publicKey,
                    status = PeerStatus.REMOVED,
                    lastSeen = existing?.lastSeen
                )
            }
        }
        return byNode.values.sortedBy { it.name ?: it.nodeId.toString() }
    }

    private fun Peer.toPeerState(): PeerState = PeerState(
        nodeId = nodeId,
        ip = ip,
        name = name,
        publicKey = publicKey,
        status = PeerStatus.ACTIVE,
        lastSeen = lastSeen
    )

    private class ObservableMeshTransport(
        private val delegate: MeshTransport
    ) : MeshTransport {
        val tcpErrors = Channel<String>(capacity = Channel.UNLIMITED)

        override suspend fun sendTcp(bytes: ByteArray, ip: String) {
            try {
                delegate.sendTcp(bytes, ip)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                tcpErrors.trySend(ip)
                throw error
            }
        }

        override suspend fun broadcastUdp(bytes: ByteArray) {
            delegate.broadcastUdp(bytes)
        }
    }

    private companion object {
        const val STREAM_BUFFER_CAPACITY = 64
    }
}
