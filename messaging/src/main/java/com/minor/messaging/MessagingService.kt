package com.minor.messaging

import com.minor.meshcontrol.DeliveryState
import com.minor.meshcontrol.MeshService
import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.Packet
import com.minor.model.Payload
import com.minor.model.Timestamp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MessagingService(
    private val ownNodeId: NodeId,
    private val meshService: MeshService,
    private val conversationStore: ConversationStore,
    private val securityCodec: MessageSecurityCodec,
    private val deliveryTimeoutMs: Long = DEFAULT_DELIVERY_TIMEOUT_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val nextLocalMessageId = AtomicLong(System.currentTimeMillis())
    private val deliveryStates = ConcurrentHashMap<Long, MessageDeliveryStatus>()

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

        _conversationsStream.value = buildConversationSummaries(conversationStore.listConversations())

        val job = SupervisorJob()
        val scope = CoroutineScope(job + dispatcher)
        serviceJob = job
        serviceScope = scope

        scope.launch { collectIncomingMessages() }
        scope.launch { collectDeliveryStatuses() }
    }

    @Synchronized
    fun stop() {
        serviceJob?.cancel()
        serviceJob = null
        serviceScope = null
        deliveryStates.clear()
    }

    fun send(destinationNodeID: NodeId, plaintext: String): Message {
        val activeScope = serviceScope ?: error("MessagingService must be started before sending messages")
        val composeTimestamp = Timestamp(System.currentTimeMillis())
        val localMessageId = MessageId(nextLocalMessageId.getAndIncrement())
        val encodedPayload = securityCodec.encode(
            plaintext = plaintext,
            recipientNodeID = destinationNodeID,
            messageID = localMessageId
        )
        val meshMessageId = MessageId(meshService.sendMessage(destinationNodeID, encodedPayload))
        val outgoingMessage = Message(
            senderNodeId = ownNodeId,
            plaintextContent = plaintext,
            composeTimestamp = composeTimestamp,
            messageId = meshMessageId,
            deliveryStatus = MessageDeliveryStatus.QUEUED
        )

        conversationStore.appendMessage(destinationNodeID, outgoingMessage)
        deliveryStates[meshMessageId.value] = MessageDeliveryStatus.QUEUED
        emitMessageUpdate(destinationNodeID, outgoingMessage, MessageDirection.OUTGOING)
        emitStatusUpdate(destinationNodeID, meshMessageId, MessageDeliveryStatus.QUEUED)
        refreshConversations()
        activeScope.launch { failMessageOnTimeout(meshMessageId) }

        return outgoingMessage
    }

    fun listConversations(): List<Conversation> =
        conversationStore.listConversations()

    fun getHistory(nodeID: NodeId): List<Message> =
        conversationStore.getConversation(nodeID)?.messages.orEmpty()

    private suspend fun collectIncomingMessages() {
        meshService.incomingMessageStream.collect { packet ->
            try {
                val decodedMessage = securityCodec.decode(packet)
                val message = Message(
                    senderNodeId = decodedMessage.senderNodeId,
                    plaintextContent = decodedMessage.plaintext,
                    composeTimestamp = decodedMessage.composeTimestamp,
                    messageId = decodedMessage.messageID,
                    deliveryStatus = MessageDeliveryStatus.DELIVERED
                )
                conversationStore.appendMessage(decodedMessage.senderNodeId, message)
                emitMessageUpdate(decodedMessage.senderNodeId, message, MessageDirection.INCOMING)
                refreshConversations()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
            }
        }
    }

    private suspend fun collectDeliveryStatuses() {
        meshService.deliveryStatusStream.collect { status ->
            val messageStatus = status.state.toMessageDeliveryStatus()
            if (!shouldApplyDeliveryStatus(status.messageId, messageStatus)) return@collect
            val storedMessage = conversationStore.updateDeliveryStatus(
                messageID = MessageId(status.messageId),
                deliveryStatus = messageStatus
            ) ?: return@collect

            emitMessageUpdate(
                nodeID = storedMessage.remoteNodeId,
                message = storedMessage.message,
                direction = storedMessage.message.directionFor(ownNodeId)
            )
            emitStatusUpdate(storedMessage.remoteNodeId, storedMessage.message.messageId, messageStatus)
            refreshConversations()
        }
    }

    private suspend fun failMessageOnTimeout(messageID: MessageId) {
        delay(deliveryTimeoutMs)
        if (!shouldApplyDeliveryStatus(messageID.value, MessageDeliveryStatus.FAILED)) return
        val storedMessage = conversationStore.updateDeliveryStatus(
            messageID = messageID,
            deliveryStatus = MessageDeliveryStatus.FAILED
        ) ?: return

        emitMessageUpdate(
            nodeID = storedMessage.remoteNodeId,
            message = storedMessage.message,
            direction = storedMessage.message.directionFor(ownNodeId)
        )
        emitStatusUpdate(storedMessage.remoteNodeId, messageID, MessageDeliveryStatus.FAILED)
        refreshConversations()
    }

    private fun shouldApplyDeliveryStatus(
        messageId: Long,
        deliveryStatus: MessageDeliveryStatus
    ): Boolean {
        val current = deliveryStates[messageId]
        if (current == MessageDeliveryStatus.DELIVERED || current == MessageDeliveryStatus.FAILED) {
            return false
        }
        deliveryStates[messageId] = deliveryStatus
        return true
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

    private fun Message.directionFor(ownNodeId: NodeId): MessageDirection =
        if (senderNodeId.toString() == ownNodeId.toString()) {
            MessageDirection.OUTGOING
        } else {
            MessageDirection.INCOMING
        }

    private fun DeliveryState.toMessageDeliveryStatus(): MessageDeliveryStatus = when (this) {
        DeliveryState.SENT -> MessageDeliveryStatus.SENT
        DeliveryState.DELIVERED -> MessageDeliveryStatus.DELIVERED
        DeliveryState.FAILED -> MessageDeliveryStatus.FAILED
    }

    private companion object {
        const val STREAM_BUFFER_CAPACITY = 64
        const val DEFAULT_DELIVERY_TIMEOUT_MS = 8_000L
    }
}

interface MessageSecurityCodec {
    fun encode(
        plaintext: String,
        recipientNodeID: NodeId,
        messageID: MessageId
    ): Payload

    fun decode(packet: Packet): DecodedMessage
}

data class DecodedMessage(
    val senderNodeId: NodeId,
    val plaintext: String,
    val composeTimestamp: Timestamp,
    val messageID: MessageId
)

data class MessageUpdate(
    val nodeID: NodeId,
    val message: Message,
    val direction: MessageDirection
)

data class MessageStatusUpdate(
    val nodeID: NodeId,
    val messageID: MessageId,
    val deliveryStatus: MessageDeliveryStatus
)

data class ConversationSummary(
    val nodeID: NodeId,
    val lastMessage: Message?,
    val unreadCount: Int
)

enum class MessageDirection {
    INCOMING,
    OUTGOING
}
