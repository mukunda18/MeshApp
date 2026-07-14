package com.meshapp.meshapp

import android.content.Context
import android.os.Build
import com.meshapp.meshapp.network.AndroidMeshSocketFactory
import com.meshapp.meshcontrol.MeshConfig
import com.meshapp.meshcontrol.MeshService
import com.meshapp.messaging.ConversationStore
import com.meshapp.messaging.MessagingService
import com.meshapp.security.Identity
import com.meshapp.security.IdentityManager
import com.meshapp.security.PersistentIdentityStore
import com.meshapp.security.Security
import com.meshapp.security.SqlNodesStore
import com.meshapp.security.NodesStore

/**
 * Manual dependency injection container for the application.
 *
 * Instantiated once inside MeshApplication.onCreate().
 * All singletons are held here and accessed via (application as MeshApplication).container.
 *
 * Lifecycle:
 *   - meshService.start()       called in MeshApplication.onCreate()
 *   - messagingService.start()  called in MeshApplication.onCreate()
 *   - meshService.stop()        suspend — called via applicationScope in onTerminate() (emulator only)
 *   - messagingService.stop()   called in MeshApplication.onTerminate()
 *
 * On a real device the process is killed by the OS and sockets are closed by the kernel.
 * Phase 3 may promote mesh to a Foreground Service for background operation.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    // ── Identity & security ───────────────────────────────────────────────────

    val nodesStore: NodesStore = try {
        SqlNodesStore(appContext)
    } catch (e: Exception) {
        // Critical: Failed to open or create the SQLite database for node identity tracking.
        throw RuntimeException("Failed to initialize SqlNodesStore", e)
    }

    val identity: Identity = try {
        IdentityManager(PersistentIdentityStore(appContext))
            .getOrGenerate(
                Build.MODEL
                    .replace(Regex("[^a-zA-Z0-9 _-]"), "")
                    .trim()
                    .take(20)
                    .ifBlank { "MeshUser" }
            )
    } catch (e: Exception) {
        // Critical: Failed to generate or retrieve this node's identity from Android Keystore.
        throw RuntimeException("Failed to get or generate identity", e)
    }

    val security: Security = try {
        Security(identity, nodesStore)
    } catch (e: Exception) {
        // Critical: Failed to initialize the cryptographic helper (e.g., missing algorithms).
        throw RuntimeException("Failed to initialize Security", e)
    }

    // ── Mesh configuration ────────────────────────────────────────────────────

    val meshConfig: MeshConfig = MeshConfig(
        udpBroadcastPort = UDP_PORT,
        tcpPort = TCP_PORT,
        ownNodeId = identity.nodeId,
        ownPublicKey = identity.publicKey,
        ownName = identity.name
    )

    // ── Core mesh service ─────────────────────────────────────────────────────

    val meshService: MeshService = MeshService(
        config = meshConfig,
        socketFactory = AndroidMeshSocketFactory(appContext),
        nodesStore = nodesStore,
        signer = security,
        verifier = security
    )

    // ── Messaging layer ───────────────────────────────────────────────────────

    val conversationStore: ConversationStore = ConversationStore(appContext)

    val messagingService: MessagingService = MessagingService(
        ownNodeId = identity.nodeId,
        meshService = meshService,
        security = security,
        conversationStore = conversationStore,
        nodesStore = nodesStore,
        identityResolutionTimeoutMs = meshConfig.identityResolutionTimeoutMs,
        streamBufferCapacity = meshConfig.streamBufferCapacity
    )

    // ── Port constants ────────────────────────────────────────────────────────

    companion object {
        /** UDP broadcast port for HELLO / RREQ / RERR packets. */
        const val UDP_PORT = 49152

        /** TCP port for unicast MESSAGE / RREP / ACK packets. */
        const val TCP_PORT = 49153
    }
}
