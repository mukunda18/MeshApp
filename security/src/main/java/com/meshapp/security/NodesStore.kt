package com.meshapp.security

import com.meshapp.model.MessageId
import com.meshapp.model.NodeId
import com.meshapp.model.PublicKey
import com.meshapp.model.Signature

/**
 * Global store for node identity mapping.
 * Both Routing (for learning names) and Security (for encryption) depend on this.
 */
interface NodesStore {
    fun addOrUpdateNode(nodeId: NodeId, name: String, publicKey: PublicKey)
    fun getPublicKey(nodeId: NodeId): PublicKey?
    fun getName(nodeId: NodeId): String?
    fun listNodes(): List<KnownNode>
}

interface PacketSigner {
    fun signAck(messageId: MessageId, status: Int): Signature
}

interface PacketVerifier {
    fun verifyAck(messageId: MessageId, status: Int, signature: Signature, senderNodeId: NodeId): Boolean
}

data class KnownNode(
    val nodeId: NodeId,
    val name: String,
    val publicKey: PublicKey
)
