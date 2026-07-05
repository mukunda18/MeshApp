package com.minor.messaging

import android.util.Log
import com.minor.meshcontrol.DeliveryState
import com.minor.meshcontrol.MeshService
import com.minor.model.MessageId
import com.minor.model.MessageProtocol
import com.minor.model.NodeId
import com.minor.model.Payload
import com.minor.model.SecureEnvelope
import com.minor.model.Timestamp
import com.minor.model.NodesStore
import com.minor.model.randomMessageId
import com.minor.security.Security
import com.minor.logger.MeshLogger
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
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _messagesStream = MutableSharedFlow<MessageUpdate>(
        extraBufferCapacity = STREAM_BUFFER_CAPACITY
    )
    val messagesStream: SharedFlow<MessageUpdate> = _messagesStream.asSharedFlow()

    private val _deliveryStatusStream = MutableSharedFlow<MessageStatusUpdate>(
        extraBufferCapacity = STREAM_BUFFER_CAPACITY
    )
    val deliveryStatusStream: SharedFlow<MessageStatusUpdate> = _deliveryStatusStream.asSharedFlow()

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
            MeshLogger.error("MessagingService", "Failed to persist outgoing message to ${destinationNodeID}", e.toString())
        }
        
        outboundChannel.trySend(OutboundRequest(destinationNodeID, outgoingMessage))
        MeshLogger.messageQueued("MessagingService", "Message ${messageId} queued for ${destinationNodeID}", plaintext)

        emitMessageUpdate(destinationNodeID, outgoingMessage, MessageDirection.OUTGOING)
        emitStatusUpdate(destinationNodeID, messageId, MessageDeliveryStatus.QUEUED)
        refreshConversations()

        return outgoingMessage
    }

    private suspend fun processOutbound(request: OutboundRequest) {
        val now = System.currentTimeMillis()
        
        // 1. Check for timeout (e.g., if the node is offline and we can't find its key)
        if (now - request.message.composeTimestamp.millis > IDENTITY_RESOLUTION_TIMEOUT_MS) {
            Log.w("MessagingService", "Identity resolution timed out for ${request.destinationNodeId}")
            MeshLogger.messageDropped("MessagingService", "Identity resolution timed out for ${request.destinationNodeId}", "MsgId: ${request.message.messageId}")
            handleDeliveryUpdate(request.message.messageId.value, DeliveryState.FAILED)
            return
        }

        val pubKey = nodesStore.getPublicKey(request.destinationNodeId)
        if (pubKey == null) {
            // Key missing: Trigger RREQ and put back in queue to retry
            MeshLogger.info("MessagingService", "Public key missing for ${request.destinationNodeId}, discovering...", "MsgId: ${request.message.messageId}")
            meshService.discoverNode(request.destinationNodeId)
            delay(2000.milliseconds) // Wait for RREP/HELLO to return
            outboundChannel.send(request)
            return
        }

        try {
            val envelopeBytes = security.encode(
                plaintext = request.message.plaintextContent,
                recipientNodeID = request.destinationNodeId,
                messageID = request.message.messageId,
                timestamp = request.message.composeTimestamp
            )
            val envelope = parseEnvelope(envelopeBytes)

            MeshLogger.messageSent("MessagingService", "Sending message ${request.message.messageId} to ${request.destinationNodeId}")
            meshService.sendMessage(request.destinationNodeId, Payload.Message(envelope), request.message.messageId)
        } catch (e: Exception) {
            Log.e("MessagingService", "Failed to encrypt message for ${request.destinationNodeId}", e)
            MeshLogger.error("MessagingService", "Failed to encrypt message for ${request.destinationNodeId}", e.toString())
            handleDeliveryUpdate(request.message.messageId.value, DeliveryState.FAILED)
        }
    }

    private fun parseEnvelope(data: ByteArray): SecureEnvelope {
        var cursor = 0
        val version = MessageProtocol.envVersion.read(data, cursor).also { cursor += it.bytesRead }.value
        val sender = MessageProtocol.senderNodeId.read(data, cursor).also { cursor += it.bytesRead }.value
        val encKey = MessageProtocol.encSymKey.read(data, cursor).also { cursor += it.bytesRead }.value
        val nonce = MessageProtocol.nonce.read(data, cursor).also { cursor += it.bytesRead }.value
        val cipher = MessageProtocol.ciphertext.read(data, cursor).also { cursor += it.bytesRead }.value
        val sig = MessageProtocol.signature.read(data, cursor).also { cursor += it.bytesRead }.value
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

    fun listConversations(): List<Conversation> =
        conversationStore.listConversations()

    fun getHistory(nodeID: NodeId): List<Message> =
        conversationStore.getConversation(nodeID)?.messages.orEmpty()

    private fun handleIncomingMessage(sourceNodeId: NodeId, payload: Payload.Message) {
        try {
            val decoded = security.decode(serializeEnvelope(payload.envelope))
            val message = Message(
                senderNodeId = decoded.senderNodeId,
                plaintextContent = decoded.content,
                composeTimestamp = decoded.timestamp,
                messageId = decoded.messageId,
                deliveryStatus = MessageDeliveryStatus.DELIVERED
            )
            MeshLogger.messageReceived("MessagingService", "Received message from ${sourceNodeId}", decoded.content)
            try {
                conversationStore.appendMessage(sourceNodeId, message)
            } catch (e: Exception) {
                // SQLiteException: Failed to save incoming message. 
                // We log it but continue so the stream update can still happen.
                Log.e("MessagingService", "Failed to save incoming message to store", e)
                MeshLogger.error("MessagingService", "Failed to save incoming message from ${sourceNodeId} to store", e.toString())
            }
            emitMessageUpdate(sourceNodeId, message, MessageDirection.INCOMING)
            refreshConversations()
        } catch (e: Exception) {
            // Possible exceptions:
            // - SecurityException (invalid signature, expired message, or malformed envelope)
            // - IllegalStateException (missing public key for sender)
            Log.w("MessagingService", "Failed to decode incoming message from ${sourceNodeId}", e)
            MeshLogger.error("MessagingService", "Failed to decode incoming message from ${sourceNodeId}", e.toString())
        }
    }

    private fun handleDeliveryUpdate(messageId: Long, state: DeliveryState) {
        val status = state.toMessageDeliveryStatus()
        val stored = conversationStore.updateDeliveryStatus(MessageId(messageId), status)
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

    private companion object {
        const val STREAM_BUFFER_CAPACITY = 64
        /** Time to wait for a node's Public Key before failing the message */
        const val IDENTITY_RESOLUTION_TIMEOUT_MS = 30_000L
    }
}

private data class OutboundRequest(
    val destinationNodeId: NodeId,
    val message: Message
)
