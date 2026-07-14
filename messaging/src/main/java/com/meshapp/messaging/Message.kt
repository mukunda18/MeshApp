package com.meshapp.messaging

import com.meshapp.model.MessageId
import com.meshapp.model.NodeId
import com.meshapp.model.Timestamp

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
