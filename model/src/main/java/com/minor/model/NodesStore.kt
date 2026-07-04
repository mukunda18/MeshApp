package com.minor.model

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

data class KnownNode(
    val nodeId: NodeId,
    val name: String,
    val publicKey: PublicKey
)
