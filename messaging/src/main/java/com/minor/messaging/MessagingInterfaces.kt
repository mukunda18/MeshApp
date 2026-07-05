package com.minor.messaging

import com.minor.model.MessageId
import com.minor.model.NodeId

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
