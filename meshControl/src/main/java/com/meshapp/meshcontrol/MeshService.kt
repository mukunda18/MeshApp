package com.meshapp.meshcontrol

import com.meshapp.model.Header
import com.meshapp.model.HeaderProtocol
import com.meshapp.model.MessageId
import com.meshapp.model.NodeId
import com.meshapp.packetprocessor.HeaderSerializer
import com.meshapp.packetprocessor.PayloadSerializer
import com.meshapp.security.NodesStore
import com.meshapp.security.PacketSigner
import com.meshapp.security.PacketVerifier
import com.meshapp.model.Payload
import com.meshapp.model.Timestamp
import com.meshapp.model.randomMessageId
import com.meshapp.logger.MeshLogger
import com.meshapp.network.MeshTransport
import com.meshapp.routing.PeerEvent
import com.meshapp.routing.RouteEvent
import com.meshapp.routing.RoutingModule
import com.meshapp.routing.SendStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground-owned mesh lifecycle coordinator.
 *
 * MeshService intentionally has no Android dependency. Android-specific code should
 * implement MeshSocketFactory to create Context-bound sockets.
 */
class MeshService(
    val config: MeshConfig,
    private val socketFactory: MeshSocketFactory,
    private val nodesStore: NodesStore,
    private val audioController: AudioController? = null,
    private val signer: PacketSigner? = null,
    private val verifier: PacketVerifier? = null
) {
    private val mutex = Mutex()
    private var routingModule: RoutingModule? = null
    private var sockets: MeshSockets? = null
    private var serviceScope: CoroutineScope? = null

    // Single source of truth for the mesh lifecycle state. Survives UI recreation
    // because MeshService is an application-scoped singleton.
    private val _stateStream = MutableStateFlow(MeshState.STOPPED)
    val stateStream: StateFlow<MeshState> = _stateStream.asStateFlow()

    val isRunning: Boolean
        get() = _stateStream.value == MeshState.RUNNING

    private val _deliveryStatusStream = MutableSharedFlow<DeliveryStatus>(extraBufferCapacity = 64)
    val deliveryStatusStream: SharedFlow<DeliveryStatus> = _deliveryStatusStream.asSharedFlow()

    private val _peerEventsStream = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 64)
    val peerEventsStream: SharedFlow<PeerEvent> = _peerEventsStream.asSharedFlow()

    private val _routeEventsStream = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 64)
    val routeEventsStream: SharedFlow<RouteEvent> = _routeEventsStream.asSharedFlow()

    private val _incomingMessageStream = MutableSharedFlow<Pair<NodeId, Payload.Message>>(extraBufferCapacity = 64)
    val incomingMessageStream: SharedFlow<Pair<NodeId, Payload.Message>> = _incomingMessageStream.asSharedFlow()

    private val _incomingVoiceStream = MutableSharedFlow<Pair<NodeId, Payload.Voice>>(extraBufferCapacity = 256)
    val incomingVoiceStream: SharedFlow<Pair<NodeId, Payload.Voice>> = _incomingVoiceStream.asSharedFlow()

    private val _incomingFileChunkStream = MutableSharedFlow<Pair<NodeId, Payload.FileChunk>>(extraBufferCapacity = 256)
    val incomingFileChunkStream: SharedFlow<Pair<NodeId, Payload.FileChunk>> = _incomingFileChunkStream.asSharedFlow()

    suspend fun start() = mutex.withLock {
        if (serviceScope != null) return@withLock // Already running

        _stateStream.value = MeshState.STARTING
        MeshLogger.info("MeshService", "Starting Mesh Service...")

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
            freshnessWindowMs = config.originTimestampFreshnessWindowMs,
            peerTimeoutMs = config.peerTimeoutMs,
            reaperCheckMs = config.peerReaperCheckMs,
            helloIntervalMs = config.helloIntervalMs,
            routeRetryBackoffMs = config.routeRetryBackoffMs
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
            rm.router.routeEvents.collect { event ->
                _routeEventsStream.emit(event)
            }
        }

        scope.launch {
            for (pair in rm.receiver.incomingPayloadChannel) {
                _incomingMessageStream.emit(pair)
            }
        }

        scope.launch {
            for (pair in rm.receiver.incomingVoiceChannel) {
                _incomingVoiceStream.emit(pair)
            }
        }

        scope.launch {
            for (pair in rm.receiver.incomingFileChunkChannel) {
                _incomingFileChunkStream.emit(pair)
            }
        }

        _stateStream.value = MeshState.RUNNING
        MeshLogger.info("MeshService", "Mesh Service RUNNING")
    }

    suspend fun stop() = mutex.withLock {
        if (serviceScope == null) return@withLock // Already stopped

        _stateStream.value = MeshState.STOPPING
        MeshLogger.info("MeshService", "Stopping Mesh Service...")

        // Ensure all audio sessions are closed and mic indicator is off
        audioController?.stopAll()

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

        _stateStream.value = MeshState.STOPPED
        MeshLogger.info("MeshService", "Mesh Service STOPPED")
    }

    /** Updates the display name used in HELLO broadcasts */
    fun updateDisplayName(newName: String) {
        routingModule?.updateDisplayName(newName)
    }

    fun sendMessage(destinationNodeID: NodeId, payload: Payload.Message, messageId: MessageId) {
        val rm = routingModule ?: error("MeshService not running")
        rm.sender.enqueue(messageId, payload, destinationNodeID)
    }

    /** Sends a VOICE payload via UDP unicast, routed if necessary */
    fun sendVoice(destinationNodeID: NodeId, payload: Payload.Voice) {
        val rm = routingModule ?: return
        val scope = serviceScope ?: return
        scope.launch {
            val nextHop = rm.router.lookup(destinationNodeID)
                ?: destinationNodeID.takeIf { rm.peers.isDirectPeer(destinationNodeID) }
            val ip = nextHop?.let { rm.peers.resolveIp(it) }
            if (ip != null) {
                // Voice payload is tiny (callId 8 + seq 4 + ts 8 + len 2 + encrypted audio ~370),
                // so a 2 KB buffer is more than enough and avoids huge allocations.
                val buf = ByteArray(2048)
                val len = PayloadSerializer.serialize(payload, buf, HeaderProtocol.HEADER_SIZE)
                val header = Header(
                    magic = HeaderProtocol.Magic.EXPECTED,
                    version = HeaderProtocol.Version.SUPPORTED_VERSION,
                    type = HeaderProtocol.Type.VOICE,
                    flags = 0,
                    hopcount = 0,
                    ttl = config.maxHopCount,
                    reserved = 0,
                    immediateSenderNodeId = config.ownNodeId,
                    sourceNodeId = config.ownNodeId,
                    destNodeId = destinationNodeID,
                    id = randomMessageId(),
                    originTimestamp = Timestamp(System.currentTimeMillis()),
                    payloadLength = len
                )
                HeaderSerializer.serialize(header, buf, 0)
                rm.sendVoice(buf.copyOfRange(0, HeaderProtocol.HEADER_SIZE + len), ip)
            } else {
                MeshLogger.error("MeshService", "No route for voice to $destinationNodeID")
            }
        }
    }

    /** Sends a FILE_CHUNK payload via TCP, routed if necessary.
     * Suspends until the chunk is written to the transport so callers can pace
     * and retry based on the result. Returns true on successful hand-off. */
    suspend fun sendFileChunk(destinationNodeID: NodeId, payload: Payload.FileChunk): Boolean {
        val rm = routingModule ?: return false
        return rm.sender.sendFileChunk(destinationNodeID, payload)
    }

    /** Triggers AODV discovery for a node (route + public key) */
    fun discoverNode(nodeId: NodeId) {
        val rm = routingModule ?: return
        val scope = serviceScope ?: return
        scope.launch {
            rm.sender.discover(nodeId)
        }
    }

    fun getRoutes() = routingModule?.router?.getRoutes() ?: emptyList()
    fun getPeers() = routingModule?.peers?.getPeers() ?: emptyList()

    private fun SendStatus.toDeliveryState() = when (this) {
        SendStatus.SENT -> DeliveryState.SENT
        SendStatus.DELIVERED -> DeliveryState.DELIVERED
        SendStatus.FAILED -> DeliveryState.FAILED
    }
}
