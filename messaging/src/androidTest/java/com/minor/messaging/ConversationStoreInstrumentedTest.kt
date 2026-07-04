package com.minor.messaging

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.Timestamp
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: ConversationStore

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        store = ConversationStore(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun appendMessageCreatesConversationAndPersistsMessage() {
        val remoteNodeId = nodeId(1)
        val message = message(senderNodeId = nodeId(2), content = "hello mesh")

        store.appendMessage(remoteNodeId, message)

        val conversation = store.getConversation(remoteNodeId)
        assertArrayEquals(remoteNodeId.bytes, conversation?.remoteNodeId?.bytes)
        assertEquals(1, conversation?.messages?.size)
        assertMessageEquals(message, conversation?.messages?.single())
    }

    @Test
    fun listConversationsReturnsMostRecentlyUpdatedFirst() {
        val olderNodeId = nodeId(1)
        val newerNodeId = nodeId(2)

        store.appendMessage(olderNodeId, message(senderNodeId = olderNodeId, timestamp = 1L))
        Thread.sleep(2L)
        store.appendMessage(newerNodeId, message(senderNodeId = newerNodeId, timestamp = 2L))

        assertEquals(
            listOf(newerNodeId.toString(), olderNodeId.toString()),
            store.listConversations().map { it.remoteNodeId.toString() }
        )
    }

    @Test
    fun deleteConversationRemovesConversationAndMessages() {
        val remoteNodeId = nodeId(1)
        store.appendMessage(remoteNodeId, message(senderNodeId = remoteNodeId))

        store.deleteConversation(remoteNodeId)

        assertNull(store.getConversation(remoteNodeId))
        assertEquals(emptyList<Conversation>(), store.listConversations())
    }

    @Test
    fun updateDeliveryStatusPersistsUpdatedMessage() {
        val remoteNodeId = nodeId(1)
        val originalMessage = message(
            senderNodeId = nodeId(2),
            timestamp = 456L
        )
        store.appendMessage(remoteNodeId, originalMessage)

        val storedMessage = store.updateDeliveryStatus(
            messageID = originalMessage.messageId,
            deliveryStatus = MessageDeliveryStatus.DELIVERED
        )

        assertArrayEquals(remoteNodeId.bytes, storedMessage?.remoteNodeId?.bytes)
        assertEquals(MessageDeliveryStatus.DELIVERED, storedMessage?.message?.deliveryStatus)
        assertEquals(
            MessageDeliveryStatus.DELIVERED,
            store.getConversation(remoteNodeId)?.messages?.single()?.deliveryStatus
        )
    }

    private fun nodeId(fill: Int): NodeId =
        NodeId(ByteArray(32) { fill.toByte() })

    private fun message(
        senderNodeId: NodeId,
        content: String = "message",
        timestamp: Long = 123L
    ): Message = Message(
        senderNodeId = senderNodeId,
        plaintextContent = content,
        composeTimestamp = Timestamp(timestamp),
        messageId = MessageId(timestamp),
        deliveryStatus = MessageDeliveryStatus.SENT
    )

    private fun assertMessageEquals(expected: Message, actual: Message?) {
        assertArrayEquals(expected.senderNodeId.bytes, actual?.senderNodeId?.bytes)
        assertEquals(expected.plaintextContent, actual?.plaintextContent)
        assertEquals(expected.composeTimestamp, actual?.composeTimestamp)
        assertEquals(expected.messageId, actual?.messageId)
        assertEquals(expected.deliveryStatus, actual?.deliveryStatus)
    }

    private companion object {
        const val DATABASE_NAME = "mesh_conversations.db"
    }
}
