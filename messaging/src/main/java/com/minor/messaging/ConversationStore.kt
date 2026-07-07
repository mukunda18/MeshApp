package com.minor.messaging

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.Timestamp
import androidx.core.database.sqlite.transaction

class ConversationStore(
    context: Context
) {
    private val helper = ConversationDatabaseHelper(context.applicationContext)

    fun getConversation(nodeID: NodeId): Conversation? {
        val db = helper.readableDatabase
        val nodeKey = nodeID.toString()
        if (!db.conversationExists(nodeKey)) return null

        return Conversation(
            remoteNodeId = nodeID,
            initialMessages = db.readMessages(nodeKey)
        )
    }

    fun listConversations(): List<Conversation> {
        val db = helper.readableDatabase
        val conversations = mutableListOf<Conversation>()
        db.query(
            TABLE_CONVERSATIONS,
            arrayOf(COL_REMOTE_NODE_ID_BYTES),
            null,
            null,
            null,
            null,
            "$COL_UPDATED_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val remoteNodeId = NodeId(cursor.getBlob(cursor.getColumnIndexOrThrow(COL_REMOTE_NODE_ID_BYTES)))
                conversations.add(
                    Conversation(
                        remoteNodeId = remoteNodeId,
                        initialMessages = db.readMessages(remoteNodeId.toString())
                    )
                )
            }
        }
        return conversations
    }

    fun appendMessage(nodeID: NodeId, message: Message) {
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                TABLE_CONVERSATIONS,
                null,
                ContentValues().apply {
                    put(COL_REMOTE_NODE_ID, nodeID.toString())
                    put(COL_REMOTE_NODE_ID_BYTES, nodeID.bytes)
                    put(COL_UPDATED_AT, now)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
            db.update(
                TABLE_CONVERSATIONS,
                ContentValues().apply { put(COL_UPDATED_AT, now) },
                "$COL_REMOTE_NODE_ID = ?",
                arrayOf(nodeID.toString())
            )
            db.insert(
                TABLE_MESSAGES,
                null,
                ContentValues().apply {
                    put(COL_REMOTE_NODE_ID, nodeID.toString())
                    put(COL_SENDER_NODE_ID_BYTES, message.senderNodeId.bytes)
                    put(COL_PLAINTEXT_CONTENT, message.plaintextContent)
                    put(COL_COMPOSE_TIMESTAMP, message.composeTimestamp.millis)
                    put(COL_MESSAGE_ID, message.messageId.value)
                    put(COL_DELIVERY_STATUS, message.deliveryStatus.name)
                }
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateDeliveryStatus(messageID: MessageId, deliveryStatus: MessageDeliveryStatus): StoredMessage? {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.update(
                TABLE_MESSAGES,
                ContentValues().apply { put(COL_DELIVERY_STATUS, deliveryStatus.name) },
                "$COL_MESSAGE_ID = ?",
                arrayOf(messageID.value.toString())
            )
            val storedMessage = db.readMessageById(messageID)
            db.setTransactionSuccessful()
            return storedMessage
        } finally {
            db.endTransaction()
        }
    }

    fun markAsRead(nodeID: NodeId) {
        val db = helper.writableDatabase
        db.transaction {
            try {
                val values = ContentValues().apply {
                    put(COL_DELIVERY_STATUS, MessageDeliveryStatus.READ.name)
                }
                update(
                    TABLE_MESSAGES,
                    values,
                    "$COL_REMOTE_NODE_ID = ? AND $COL_DELIVERY_STATUS = ?",
                    arrayOf(nodeID.toString(), MessageDeliveryStatus.DELIVERED.name)
                )
            } finally {
            }
        }
    }

    fun deleteConversation(nodeID: NodeId) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val args = arrayOf(nodeID.toString())
            db.delete(TABLE_MESSAGES, "$COL_REMOTE_NODE_ID = ?", args)
            db.delete(TABLE_CONVERSATIONS, "$COL_REMOTE_NODE_ID = ?", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun SQLiteDatabase.conversationExists(nodeKey: String): Boolean {
        query(
            TABLE_CONVERSATIONS,
            arrayOf(COL_REMOTE_NODE_ID),
            "$COL_REMOTE_NODE_ID = ?",
            arrayOf(nodeKey),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun SQLiteDatabase.readMessages(nodeKey: String): List<Message> {
        val messages = mutableListOf<Message>()
        query(
            TABLE_MESSAGES,
            arrayOf(
                COL_SENDER_NODE_ID_BYTES,
                COL_PLAINTEXT_CONTENT,
                COL_COMPOSE_TIMESTAMP,
                COL_MESSAGE_ID,
                COL_DELIVERY_STATUS
            ),
            "$COL_REMOTE_NODE_ID = ?",
            arrayOf(nodeKey),
            null,
            null,
            "$COL_ROW_ID ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(cursor.toMessage())
            }
        }
        return messages
    }

    private fun SQLiteDatabase.readMessageById(messageID: MessageId): StoredMessage? {
        rawQuery(
            """
            SELECT
                c.$COL_REMOTE_NODE_ID_BYTES,
                m.$COL_SENDER_NODE_ID_BYTES,
                m.$COL_PLAINTEXT_CONTENT,
                m.$COL_COMPOSE_TIMESTAMP,
                m.$COL_MESSAGE_ID,
                m.$COL_DELIVERY_STATUS
            FROM $TABLE_MESSAGES m
            INNER JOIN $TABLE_CONVERSATIONS c
                ON c.$COL_REMOTE_NODE_ID = m.$COL_REMOTE_NODE_ID
            WHERE m.$COL_MESSAGE_ID = ?
            ORDER BY m.$COL_ROW_ID DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(messageID.value.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return StoredMessage(
                remoteNodeId = NodeId(cursor.getBlob(cursor.getColumnIndexOrThrow(COL_REMOTE_NODE_ID_BYTES))),
                message = cursor.toMessage()
            )
        }
    }

    private fun Cursor.toMessage(): Message = Message(
        senderNodeId = NodeId(getBlob(getColumnIndexOrThrow(COL_SENDER_NODE_ID_BYTES))),
        plaintextContent = getString(getColumnIndexOrThrow(COL_PLAINTEXT_CONTENT)),
        composeTimestamp = Timestamp(getLong(getColumnIndexOrThrow(COL_COMPOSE_TIMESTAMP))),
        messageId = MessageId(getLong(getColumnIndexOrThrow(COL_MESSAGE_ID))),
        deliveryStatus = MessageDeliveryStatus.valueOf(
            getString(getColumnIndexOrThrow(COL_DELIVERY_STATUS))
        )
    )

    private class ConversationDatabaseHelper(
        context: Context
    ) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_CONVERSATIONS (
                    $COL_REMOTE_NODE_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_REMOTE_NODE_ID_BYTES BLOB NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE $TABLE_MESSAGES (
                    $COL_ROW_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_REMOTE_NODE_ID TEXT NOT NULL,
                    $COL_SENDER_NODE_ID_BYTES BLOB NOT NULL,
                    $COL_PLAINTEXT_CONTENT TEXT NOT NULL,
                    $COL_COMPOSE_TIMESTAMP INTEGER NOT NULL,
                    $COL_MESSAGE_ID INTEGER NOT NULL,
                    $COL_DELIVERY_STATUS TEXT NOT NULL,
                    FOREIGN KEY($COL_REMOTE_NODE_ID)
                        REFERENCES $TABLE_CONVERSATIONS($COL_REMOTE_NODE_ID)
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX ${TABLE_MESSAGES}_${COL_REMOTE_NODE_ID}_idx " +
                    "ON $TABLE_MESSAGES($COL_REMOTE_NODE_ID)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CONVERSATIONS")
            onCreate(db)
        }
    }

    private companion object {
        const val DATABASE_NAME = "mesh_conversations.db"
        const val DATABASE_VERSION = 1

        const val TABLE_CONVERSATIONS = "conversations"
        const val TABLE_MESSAGES = "messages"

        const val COL_ROW_ID = "_id"
        const val COL_REMOTE_NODE_ID = "remote_node_id"
        const val COL_REMOTE_NODE_ID_BYTES = "remote_node_id_bytes"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_SENDER_NODE_ID_BYTES = "sender_node_id_bytes"
        const val COL_PLAINTEXT_CONTENT = "plaintext_content"
        const val COL_COMPOSE_TIMESTAMP = "compose_timestamp"
        const val COL_MESSAGE_ID = "message_id"
        const val COL_DELIVERY_STATUS = "delivery_status"
    }
}

data class StoredMessage(
    val remoteNodeId: NodeId,
    val message: Message
)
