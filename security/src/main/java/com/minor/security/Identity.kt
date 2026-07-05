package com.minor.security

import com.minor.model.NodeId
import com.minor.model.PublicKey
import java.security.MessageDigest

/**
 * Owns this node's persistent identity.
 * nodeID is SHA-256(publicKey).
 */
data class Identity(
    val nodeId: NodeId,
    val name: String,
    val publicKey: PublicKey,
    val privateKey: ByteArray 
) {
    companion object {
        fun deriveNodeId(publicKey: PublicKey): NodeId {
            val digest = MessageDigest.getInstance("SHA-256")
            return NodeId(digest.digest(publicKey.bytes))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Identity
        if (nodeId != other.nodeId) return false
        if (name != other.name) return false
        if (publicKey != other.publicKey) return false
        if (!privateKey.contentEquals(other.privateKey)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

interface IdentityStore {
    fun getIdentity(): Identity?
    fun saveIdentity(identity: Identity)
}
