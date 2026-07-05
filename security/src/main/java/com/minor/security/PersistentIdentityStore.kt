package com.minor.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.minor.model.NodeId
import com.minor.model.PublicKey as MeshPublicKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PersistentIdentityStore(context: Context) : IdentityStore {
    private val sharedPrefs = context.getSharedPreferences("mesh_identity_v2", Context.MODE_PRIVATE)
    private val keyAlias = "mesh_identity_master_key"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(keyAlias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(data.encodeToByteArray())
        val iv = cipher.iv
        // Store as [IV(12) | Ciphertext(var)]
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedData: String?): String? {
        if (encryptedData == null) return null
        return try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).decodeToString()
        } catch (_: Exception) {
            null
        }
    }

    override fun getIdentity(): Identity? {
        val nodeIdHex = decrypt(sharedPrefs.getString(KEY_NODE_ID, null)) ?: return null
        val name = decrypt(sharedPrefs.getString(KEY_NAME, null)) ?: return null
        val publicKeyB64 = decrypt(sharedPrefs.getString(KEY_PUBLIC_KEY, null)) ?: return null
        val privateKeyB64 = decrypt(sharedPrefs.getString(KEY_PRIVATE_KEY, null)) ?: return null

        return try {
            Identity(
                nodeId = NodeId(hexToBytes(nodeIdHex)),
                name = name,
                publicKey = MeshPublicKey(Base64.decode(publicKeyB64, Base64.NO_WRAP)),
                privateKey = Base64.decode(privateKeyB64, Base64.NO_WRAP)
            )
        } catch (_: Exception) {
            // Stored identity is invalid (e.g. key from a prior format). Clear and regenerate.
            sharedPrefs.edit { clear() }
            null
        }
    }

    override fun saveIdentity(identity: Identity) {
        sharedPrefs.edit {
            putString(KEY_NODE_ID, encrypt(identity.nodeId.toString()))
            putString(KEY_NAME, encrypt(identity.name))
            putString(
                KEY_PUBLIC_KEY,
                encrypt(Base64.encodeToString(identity.publicKey.bytes, Base64.NO_WRAP))
            )
            putString(
                KEY_PRIVATE_KEY,
                encrypt(Base64.encodeToString(identity.privateKey, Base64.NO_WRAP))
            )
        }
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
        private const val KEY_NODE_ID = "node_id_v2"
        private const val KEY_NAME = "name_v2"
        private const val KEY_PUBLIC_KEY = "public_key_v2"
        private const val KEY_PRIVATE_KEY = "private_key_v2"
    }
}
