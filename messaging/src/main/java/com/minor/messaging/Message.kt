package com.minor.messaging

import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.Timestamp

enum class MessageDeliveryStatus {
    QUEUED,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

data class Message(
    val senderNodeId: NodeId,
    val plaintextContent: String,
    val composeTimestamp: Timestamp,
    val messageId: MessageId,
    val deliveryStatus: MessageDeliveryStatus
)
