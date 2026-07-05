package com.minor.security

import com.minor.model.PublicKey
import java.security.KeyPairGenerator
import java.security.Signature

class IdentityManager(
    private val identityStore: IdentityStore
) {
    private val keyPairGen by lazy {
        KeyPairGenerator.getInstance("EC").apply {
            initialize(256)  // NIST P-256 curve
        }
    }

    fun getOrGenerate(name: String): Identity {
        val existing = identityStore.getIdentity()
        if (existing != null) return existing

        try {
            // Generate new ECDSA keypair (P-256)
            val keyPair = keyPairGen.generateKeyPair()
            val publicKeyBytes = keyPair.public.encoded
            val privateKeyBytes = keyPair.private.encoded
            
            val publicKey = PublicKey(publicKeyBytes)
            val nodeId = Identity.deriveNodeId(publicKey)
            
            val newIdentity = Identity(
                nodeId = nodeId,
                name = name,
                publicKey = publicKey,
                privateKey = privateKeyBytes
            )
            
            identityStore.saveIdentity(newIdentity)
            return newIdentity
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate identity", e)
        }
    }
}
