package com.meshapp.messaging

import com.meshapp.model.MessageId
import com.meshapp.model.NodeId
import com.meshapp.model.Timestamp

class Conversation(
    val remoteNodeId: NodeId,
    initialMessages: List<Message> = emptyList()
) {
    private val orderedMessages = initialMessages.toMutableList()

    val messages: List<Message>
        get() = orderedMessages.toList()

    fun appendOutgoing(
        senderNodeId: NodeId,
        plaintextContent: String,
        composeTimestamp: Timestamp,
        messageId: MessageId,
        deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.QUEUED
    ): Message {
        val message = Message(
            senderNodeId = senderNodeId,
            plaintextContent = plaintextContent,
            composeTimestamp = composeTimestamp,
            messageId = messageId,
            deliveryStatus = deliveryStatus
        )
        orderedMessages.add(message)
        return message
    }

    fun appendIncoming(
        plaintextContent: String,
        composeTimestamp: Timestamp,
        messageId: MessageId,
        deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.DELIVERED
    ): Message {
        val message = Message(
            senderNodeId = remoteNodeId,
            plaintextContent = plaintextContent,
            composeTimestamp = composeTimestamp,
            messageId = messageId,
            deliveryStatus = deliveryStatus
        )
        orderedMessages.add(message)
        return message
    }
}
