package com.meshapp.security

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.meshapp.model.NodeId
import com.meshapp.model.PublicKey

class SqlNodesStore(context: Context) : NodesStore {
    private val helper = NodesDatabaseHelper(context.applicationContext)

    override fun addOrUpdateNode(nodeId: NodeId, name: String, publicKey: PublicKey) {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put(COL_NODE_ID, nodeId.toString())
            put(COL_NAME, name)
            put(COL_PUBLIC_KEY, publicKey.bytes)
        }
        db.insertWithOnConflict(TABLE_NODES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun getPublicKey(nodeId: NodeId): PublicKey? {
        val db = helper.readableDatabase
        db.query(
            TABLE_NODES,
            arrayOf(COL_PUBLIC_KEY),
            "$COL_NODE_ID = ?",
            arrayOf(nodeId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return PublicKey(cursor.getBlob(cursor.getColumnIndexOrThrow(COL_PUBLIC_KEY)))
            }
        }
        return null
    }

    override fun getName(nodeId: NodeId): String? {
        val db = helper.readableDatabase
        db.query(
            TABLE_NODES,
            arrayOf(COL_NAME),
            "$COL_NODE_ID = ?",
            arrayOf(nodeId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
            }
        }
        return null
    }

    override fun listNodes(): List<KnownNode> {
        val db = helper.readableDatabase
        val nodes = mutableListOf<KnownNode>()
        db.query(
            TABLE_NODES,
            arrayOf(COL_NODE_ID, COL_NAME, COL_PUBLIC_KEY),
            null,
            null,
            null,
            null,
            "$COL_NAME ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val nodeIdHex = cursor.getString(cursor.getColumnIndexOrThrow(COL_NODE_ID))
                nodes.add(
                    KnownNode(
                        nodeId = NodeId(hexToBytes(nodeIdHex)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                        publicKey = PublicKey(cursor.getBlob(cursor.getColumnIndexOrThrow(COL_PUBLIC_KEY)))
                    )
                )
            }
        }
        return nodes
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            val firstDigit = Character.digit(hex[i], 16)
            val secondDigit = Character.digit(hex[i + 1], 16)
            result[i / 2] = ((firstDigit shl 4) + secondDigit).toByte()
        }
        return result
    }

    private class NodesDatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_NODES (
                    $COL_NODE_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_NAME TEXT NOT NULL,
                    $COL_PUBLIC_KEY BLOB NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NODES")
            onCreate(db)
        }
    }

    companion object {
        private const val DATABASE_NAME = "mesh_nodes.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NODES = "nodes"
        private const val COL_NODE_ID = "node_id"
        private const val COL_NAME = "name"
        private const val COL_PUBLIC_KEY = "public_key"
    }
}
