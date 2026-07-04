package com.minor.security

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.minor.model.PublicKey

class IdentityManager(
    private val identityStore: IdentityStore
) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    fun getOrGenerate(name: String): Identity {
        val existing = identityStore.getIdentity()
        if (existing != null) return existing

        // Generate new Ed25519 keypair
        val keyPair = sodium.cryptoSignKeypair()
        val publicKey = PublicKey(keyPair.publicKey.asBytes)
        // sodium.cryptoSignKeypair returns a 64-byte secret key (seed + public key)
        val privateKey = keyPair.secretKey.asBytes 
        
        val nodeId = Identity.deriveNodeId(publicKey)
        
        val newIdentity = Identity(
            nodeId = nodeId,
            name = name,
            publicKey = publicKey,
            privateKey = privateKey
        )
        
        identityStore.saveIdentity(newIdentity)
        return newIdentity
    }
}
