package com.minor.messaging

import com.minor.meshcontrol.DeliveryState
import com.minor.meshcontrol.MeshService
import com.minor.model.MessageId
import com.minor.model.MessageProtocol
import com.minor.model.NodeId
import com.minor.model.Payload
import com.minor.model.SecureEnvelope
import com.minor.model.Timestamp
import com.minor.model.randomMessageId
import com.minor.security.Security
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MessagingService(
    private val ownNodeId: NodeId,
    private val meshService: MeshService,
    private val security: Security,
    private val conversationStore: ConversationStore,
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

        conversationStore.appendMessage(destinationNodeID, outgoingMessage)
        
        val envelopeBytes = security.encode(plaintext, destinationNodeID, messageId)
        val envelope = parseEnvelope(envelopeBytes)

        meshService.sendMessage(destinationNodeID, Payload.Message(envelope), messageId)

        emitMessageUpdate(destinationNodeID, outgoingMessage, MessageDirection.OUTGOING)
        emitStatusUpdate(destinationNodeID, messageId, MessageDeliveryStatus.QUEUED)
        refreshConversations()

        return outgoingMessage
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
        val buf = ByteArray(2048)
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
            conversationStore.appendMessage(sourceNodeId, message)
            emitMessageUpdate(sourceNodeId, message, MessageDirection.INCOMING)
            refreshConversations()
        } catch (e: Exception) {
            // Log decryption failure
        }
    }

    private fun handleDeliveryUpdate(messageId: Long, state: DeliveryState) {
        val status = state.toMessageDeliveryStatus()
        val stored = conversationStore.updateDeliveryStatus(MessageId(messageId), status)
        if (stored != null) {
            emitMessageUpdate(
                nodeID = stored.remoteNodeId,
                message = stored.message,
                direction = if (stored.message.senderNodeId.toString() == ownNodeId.toString()) 
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
                    message.senderNodeId.toString() != ownNodeId.toString() &&
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
    }
}
