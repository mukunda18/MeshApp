package com.minor.meshapp.identity

import android.content.Context
import android.os.Build
import com.minor.model.NodeId
import com.minor.model.PublicKey
import java.security.SecureRandom

/**
 * Loads or generates the persistent mesh identity for this device.
 *
 * On first run:
 *   - Generates a 32-byte cryptographically-random NodeId and persists it.
 *   - Derives displayName from Build.MODEL (trimmed to 20 chars).
 *
 * On subsequent runs:
 *   - Loads the persisted NodeId.
 *
 * PublicKey is a 32-byte zero placeholder until the :security module is
 * implemented with real Ed25519 key management.
 *
 * Phase 3 or the security module implementation will replace the PublicKey
 * placeholder with a real key derived from a persisted Ed25519 key pair.
 */
class IdentityStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val nodeId: NodeId = loadOrGenerateNodeId()

    /** Placeholder until :security provides real key management. */
    val publicKey: PublicKey = PublicKey(ByteArray(32))

    val displayName: String = loadOrGenerateName()

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun loadOrGenerateNodeId(): NodeId {
        val hex = prefs.getString(KEY_NODE_ID, null)
        if (hex != null) {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return NodeId(bytes)
        }
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        prefs.edit()
            .putString(KEY_NODE_ID, bytes.joinToString("") { "%02x".format(it) })
            .apply()
        return NodeId(bytes)
    }

    private fun loadOrGenerateName(): String {
        val saved = prefs.getString(KEY_DISPLAY_NAME, null)
        if (saved != null) return saved
        val name = Build.MODEL
            .replace(Regex("[^a-zA-Z0-9 _-]"), "")
            .trim()
            .take(20)
            .ifBlank { DEFAULT_NAME }
        prefs.edit().putString(KEY_DISPLAY_NAME, name).apply()
        return name
    }

    // ── Companion ────────────────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME = "mesh_identity"
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val DEFAULT_NAME = "MeshUser"
    }
}
