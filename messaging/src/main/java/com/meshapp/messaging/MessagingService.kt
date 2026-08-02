package com.meshapp.messaging

import android.util.Log
import com.meshapp.meshcontrol.DeliveryState
import com.meshapp.meshcontrol.MeshService
import com.meshapp.model.CallSignal
import com.meshapp.model.CallSignalProtocol
import com.meshapp.model.ContentType
import com.meshapp.model.MessageId
import com.meshapp.model.MessageProtocol
import com.meshapp.model.NodeId
import com.meshapp.model.Payload
import com.meshapp.model.SecureEnvelope
import com.meshapp.model.Timestamp
import com.meshapp.security.NodesStore
import com.meshapp.model.randomMessageId
import com.meshapp.security.Security
import com.meshapp.logger.MeshLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MessagingService(
    private val ownNodeId: NodeId,
    private val meshService: MeshService,
    private val security: Security,
    private val conversationStore: ConversationStore,
    private val nodesStore: NodesStore,
    private val identityResolutionTimeoutMs: Long,
    streamBufferCapacity: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _messagesStream = MutableSharedFlow<MessageUpdate>(
        extraBufferCapacity = streamBufferCapacity
    )
    val messagesStream: SharedFlow<MessageUpdate> = _messagesStream.asSharedFlow()

    private val _deliveryStatusStream = MutableSharedFlow<MessageStatusUpdate>(
        extraBufferCapacity = streamBufferCapacity
    )

    private val _callSignalsStream = MutableSharedFlow<Pair<NodeId, CallSignal>>(
        extraBufferCapacity = streamBufferCapacity
    )
    val callSignalsStream: SharedFlow<Pair<NodeId, CallSignal>> = _callSignalsStream.asSharedFlow()

    private val _conversationsStream = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversationsStream: StateFlow<List<ConversationSummary>> = _conversationsStream.asStateFlow()

    private val outboundChannel = Channel<OutboundRequest>(Channel.UNLIMITED)

    private var serviceJob: Job? = null
    private var serviceScope: CoroutineScope? = null

    @Synchronized
    fun start() {
        if (serviceJob?.isActive == true) return

        refreshConversations()

        val job = SupervisorJob()
        val scope = CoroutineScope(job + dispatcher)
        serviceJob = job
        serviceScope = scope

        scope.launch {
            meshService.incomingMessageStream.collect { (sourceNodeId, payload) ->
                handleIncomingMessage(sourceNodeId, payload)
            }
        }

        scope.launch {
            meshService.deliveryStatusStream.collect { status ->
                handleDeliveryUpdate(status.messageId, status.state)
            }
        }

        scope.launch {
            for (request in outboundChannel) {
                processOutbound(request)
            }
        }
    }

    @Synchronized
    fun stop() {
        serviceJob?.cancel()
        serviceJob = null
        serviceScope = null
    }

    fun send(destinationNodeID: NodeId, plaintext: String): Message {
        if (destinationNodeID.bytes.contentEquals(ownNodeId.bytes)) {
            MeshLogger.error("MessagingService", "Cannot send message to self")
            throw IllegalArgumentException("Cannot send message to self")
        }

        val composeTimestamp = Timestamp(System.currentTimeMillis())
        val messageId = randomMessageId()
        
        val outgoingMessage = Message(
            senderNodeId = ownNodeId,
            plaintextContent = plaintext,
            composeTimestamp = composeTimestamp,
            messageId = messageId,
            deliveryStatus = MessageDeliveryStatus.QUEUED
        )

        try {
            conversationStore.appendMessage(destinationNodeID, outgoingMessage)
        } catch (e: Exception) {
            // Possible exceptions:
            // - SQLiteException (database full, disk I/O error, or corruption)
            Log.e("MessagingService", "Failed to persist outgoing message", e)
            MeshLogger.error("MessagingService", "Failed to persist outgoing message to $destinationNodeID", e.toString())
        }
        
        outboundChannel.trySend(OutboundRequest.Chat(destinationNodeID, outgoingMessage))
        MeshLogger.messageQueued("MessagingService", "Message $messageId queued for $destinationNodeID", plaintext)

        emitMessageUpdate(destinationNodeID, outgoingMessage, MessageDirection.OUTGOING)
        emitStatusUpdate(destinationNodeID, messageId, MessageDeliveryStatus.QUEUED)
        refreshConversations()

        return outgoingMessage
    }

    fun sendCallSignal(destination: NodeId, signal: CallSignal) {
        val timestamp = Timestamp(System.currentTimeMillis())
        outboundChannel.trySend(OutboundRequest.Signal(destination, signal, timestamp))
        MeshLogger.info("MessagingService", "Call signal ${signal.type} queued for $destination")
    }

    private fun processOutbound(request: OutboundRequest) {
        val now = System.currentTimeMillis()
        val composeTime = when (request) {
            is OutboundRequest.Chat -> request.message.composeTimestamp.millis
            is OutboundRequest.Signal -> request.timestamp.millis
        }

        // 1. Check for timeout (e.g., if the node is offline, and we can't find its key)
        if (now - composeTime > identityResolutionTimeoutMs) {
            Log.w("MessagingService", "Identity resolution timed out for ${request.destinationNodeId}")
            if (request is OutboundRequest.Chat) {
                MeshLogger.messageDropped("MessagingService", "Identity resolution timed out for ${request.destinationNodeId}", "MsgId: ${request.message.messageId}")
                handleDeliveryUpdate(request.message.messageId, DeliveryState.FAILED)
            }
            return
        }

        val pubKey = nodesStore.getPublicKey(request.destinationNodeId)
        if (pubKey == null) {
            // Key missing: Trigger RREQ and put back in queue to retry
            MeshLogger.info("MessagingService", "Public key missing for ${request.destinationNodeId}, discovering...")
            meshService.discoverNode(request.destinationNodeId)
            
            // Unblock the main loop: delay and re-enqueue in the background
            serviceScope?.launch {
                delay(2000.milliseconds) 
                outboundChannel.send(request)
            }
            return
        }

        try {
            val (payloadBytes, contentType, transportMessageId, timestamp) = when (request) {
                is OutboundRequest.Chat -> {
                    val bytes = request.message.plaintextContent.encodeToByteArray()
                    Quad(bytes, ContentType.CHAT, request.message.messageId, request.message.composeTimestamp)
                }
                is OutboundRequest.Signal -> {
                    // Estimate size for buffer
                    val buf = ByteArray(1024) 
                    val size = CallSignalProtocol.callSignal.write(buf, request.signal, 0)
                    // Use random ID for transport to avoid deduplication, but callId remains inside.
                    Quad(buf.copyOfRange(0, size), ContentType.CALL_SIGNAL, randomMessageId(), request.timestamp)
                }
            }

            val envelopeBytes = security.encode(
                payload = payloadBytes,
                contentType = contentType,
                recipientNodeID = request.destinationNodeId,
                messageID = transportMessageId,
                timestamp = timestamp
            )
            val envelope = parseEnvelope(envelopeBytes)

            MeshLogger.info("MessagingService", "Sending ${if (contentType == ContentType.CHAT) "chat" else "signal"} to ${request.destinationNodeId}")
            meshService.sendMessage(request.destinationNodeId, Payload.Message(envelope), transportMessageId)
        } catch (e: Exception) {
            Log.e("MessagingService", "Failed to encrypt for ${request.destinationNodeId}", e)
            MeshLogger.error("MessagingService", "Failed to encrypt for ${request.destinationNodeId}", e.toString())
            if (request is OutboundRequest.Chat) {
                handleDeliveryUpdate(request.message.messageId, DeliveryState.FAILED)
            }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun parseEnvelope(data: ByteArray): SecureEnvelope {
        var cursor = 0
        val version = MessageProtocol.envVersion.read(data, cursor).also { cursor += it.bytesRead }.value
        val sender = MessageProtocol.senderNodeId.read(data, cursor).also { cursor += it.bytesRead }.value
        val encKey = MessageProtocol.encSymKey.read(data, cursor).also { cursor += it.bytesRead }.value
        val nonce = MessageProtocol.nonce.read(data, cursor).also { cursor += it.bytesRead }.value
        val cipher = MessageProtocol.ciphertext.read(data, cursor).also { cursor += it.bytesRead }.value
        val sig = MessageProtocol.signature.read(data, cursor).value
        return SecureEnvelope(version, sender, encKey, nonce, cipher, sig)
    }

    private fun serializeEnvelope(env: SecureEnvelope): ByteArray {
        val envelopeSize = MessageProtocol.ENV_VERSION_LENGTH +
            MessageProtocol.SENDER_NODE_ID_LENGTH +
            MessageProtocol.ENC_SYM_KEY_LENGTH +
            MessageProtocol.NONCE_LENGTH +
            MessageProtocol.CIPHER_LEN_LENGTH + env.ciphertext.size +
            MessageProtocol.SIGNATURE_LENGTH
        val buf = ByteArray(envelopeSize)
        var cursor = 0
        cursor += MessageProtocol.envVersion.write(buf, env.envVersion, cursor)
        cursor += MessageProtocol.senderNodeId.write(buf, env.senderNodeId, cursor)
        cursor += MessageProtocol.encSymKey.write(buf, env.encSymKey, cursor)
        cursor += MessageProtocol.nonce.write(buf, env.nonce, cursor)
        cursor += MessageProtocol.ciphertext.write(buf, env.ciphertext, cursor)
        cursor += MessageProtocol.signature.write(buf, env.signature, cursor)
        return buf.copyOfRange(0, cursor)
    }

    fun getHistory(nodeID: NodeId): List<Message> =
        conversationStore.getConversation(nodeID)?.messages.orEmpty()

    fun markConversationAsRead(nodeID: NodeId) {
        conversationStore.markAsRead(nodeID)
        refreshConversations()
    }

    private fun handleIncomingMessage(sourceNodeId: NodeId, payload: Payload.Message) {
        try {
            val decoded = security.decode(serializeEnvelope(payload.envelope))
            
            if (decoded.contentType == ContentType.CHAT) {
                val contentString = decoded.content.decodeToString()
                val message = Message(
                    senderNodeId = decoded.senderNodeId,
                    plaintextContent = contentString,
                    composeTimestamp = decoded.timestamp,
                    messageId = decoded.messageId,
                    deliveryStatus = MessageDeliveryStatus.DELIVERED
                )
                MeshLogger.messageReceived("MessagingService", "Received message from $sourceNodeId", contentString)
                try {
                    conversationStore.appendMessage(sourceNodeId, message)
                } catch (e: Exception) {
                    // SQLiteException: Failed to save incoming message. 
                    // We log it but continue so the stream update can still happen.
                    Log.e("MessagingService", "Failed to save incoming message to store", e)
                    MeshLogger.error("MessagingService", "Failed to save incoming message from $sourceNodeId to store", e.toString())
                }
                emitMessageUpdate(sourceNodeId, message, MessageDirection.INCOMING)
                refreshConversations()
            } else if (decoded.contentType == ContentType.CALL_SIGNAL) {
                try {
                    val signalRead = CallSignalProtocol.callSignal.read(decoded.content, 0)
                    _callSignalsStream.tryEmit(sourceNodeId to signalRead.value)
                    MeshLogger.info("MessagingService", "Received call signal ${signalRead.value.type} from $sourceNodeId")
                } catch (e: Exception) {
                    Log.e("MessagingService", "Failed to parse call signal from $sourceNodeId", e)
                }
            }
        } catch (e: Exception) {
            // Possible exceptions:
            // - SecurityException (invalid signature, expired message, or malformed envelope)
            // - IllegalStateException (missing public key for sender)
            Log.w("MessagingService", "Failed to decode incoming message from $sourceNodeId", e)
            MeshLogger.error("MessagingService", "Failed to decode incoming message from $sourceNodeId", e.toString())
        }
    }

    private fun handleDeliveryUpdate(messageId: MessageId, state: DeliveryState) {
        val status = state.toMessageDeliveryStatus()
        val stored = conversationStore.updateDeliveryStatus(messageId, status)
        if (stored != null) {
            emitMessageUpdate(
                nodeID = stored.remoteNodeId,
                message = stored.message,
                direction = if (stored.message.senderNodeId.bytes.contentEquals(ownNodeId.bytes))
                    MessageDirection.OUTGOING else MessageDirection.INCOMING
            )
            emitStatusUpdate(stored.remoteNodeId, stored.message.messageId, status)
            refreshConversations()
        }
    }

    private fun emitMessageUpdate(nodeID: NodeId, message: Message, direction: MessageDirection) {
        _messagesStream.tryEmit(
            MessageUpdate(
                nodeID = nodeID,
                message = message,
                direction = direction
            )
        )
    }

    private fun emitStatusUpdate(
        nodeID: NodeId,
        messageID: MessageId,
        deliveryStatus: MessageDeliveryStatus
    ) {
        _deliveryStatusStream.tryEmit(
            MessageStatusUpdate(
                nodeID = nodeID,
                messageID = messageID,
                deliveryStatus = deliveryStatus
            )
        )
    }

    private fun refreshConversations() {
        _conversationsStream.value = buildConversationSummaries(conversationStore.listConversations())
    }

    private fun buildConversationSummaries(conversations: List<Conversation>): List<ConversationSummary> =
        conversations.map { conversation ->
            ConversationSummary(
                nodeID = conversation.remoteNodeId,
                lastMessage = conversation.messages.lastOrNull(),
                unreadCount = conversation.messages.count { message ->
                    !message.senderNodeId.bytes.contentEquals(ownNodeId.bytes) &&
                        message.deliveryStatus == MessageDeliveryStatus.DELIVERED
                }
            )
        }

    private fun DeliveryState.toMessageDeliveryStatus(): MessageDeliveryStatus = when (this) {
        DeliveryState.SENT -> MessageDeliveryStatus.SENT
        DeliveryState.DELIVERED -> MessageDeliveryStatus.DELIVERED
        DeliveryState.FAILED -> MessageDeliveryStatus.FAILED
    }
}

private sealed class OutboundRequest {
    abstract val destinationNodeId: NodeId

    data class Chat(
        override val destinationNodeId: NodeId,
        val message: Message
    ) : OutboundRequest()

    data class Signal(
        override val destinationNodeId: NodeId,
        val signal: CallSignal,
        val timestamp: Timestamp
    ) : OutboundRequest()
}
