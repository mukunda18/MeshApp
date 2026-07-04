package com.minor.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.minor.model.NodeId
import com.minor.model.PublicKey as MeshPublicKey
import android.util.Base64

class PersistentIdentityStore(context: Context) : IdentityStore {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "mesh_identity",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun getIdentity(): Identity? {
        val nodeIdHex = sharedPrefs.getString(KEY_NODE_ID, null) ?: return null
        val name = sharedPrefs.getString(KEY_NAME, null) ?: return null
        val publicKeyB64 = sharedPrefs.getString(KEY_PUBLIC_KEY, null) ?: return null
        val privateKeyB64 = sharedPrefs.getString(KEY_PRIVATE_KEY, null) ?: return null

        return Identity(
            nodeId = NodeId(hexToBytes(nodeIdHex)),
            name = name,
            publicKey = MeshPublicKey(Base64.decode(publicKeyB64, Base64.NO_WRAP)),
            privateKey = Base64.decode(privateKeyB64, Base64.NO_WRAP)
        )
    }

    override fun saveIdentity(identity: Identity) {
        sharedPrefs.edit()
            .putString(KEY_NODE_ID, identity.nodeId.toString())
            .putString(KEY_NAME, identity.name)
            .putString(KEY_PUBLIC_KEY, Base64.encodeToString(identity.publicKey.bytes, Base64.NO_WRAP))
            .putString(KEY_PRIVATE_KEY, Base64.encodeToString(identity.privateKey, Base64.NO_WRAP))
            .apply()
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

    companion object {
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_NAME = "name"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_PRIVATE_KEY = "private_key"
    }
}
