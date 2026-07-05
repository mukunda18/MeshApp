package com.minor.meshapp

import android.content.Context
import com.minor.meshapp.identity.IdentityStore
import com.minor.meshapp.network.AndroidMeshSocketFactory
import com.minor.meshcontrol.MeshConfig
import com.minor.meshcontrol.MeshService
import com.minor.messaging.ConversationStore
import com.minor.messaging.MessagingService
import com.minor.security.PassthroughSecurityCodec

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

    // ── Identity ─────────────────────────────────────────────────────────────

    val identity: IdentityStore = IdentityStore(appContext)

    // ── Mesh configuration ────────────────────────────────────────────────────

    val meshConfig: MeshConfig = MeshConfig(
        udpBroadcastPort = UDP_PORT,
        tcpPort = TCP_PORT,
        ownNodeId = identity.nodeId,
        ownPublicKey = identity.publicKey,
        ownName = identity.displayName
    )

    // ── Core mesh service ─────────────────────────────────────────────────────

    val meshService: MeshService = MeshService(
        config = meshConfig,
        socketFactory = AndroidMeshSocketFactory(appContext)
    )

    // ── Messaging layer ───────────────────────────────────────────────────────

    val conversationStore: ConversationStore = ConversationStore(appContext)

    val messagingService: MessagingService = MessagingService(
        ownNodeId = identity.nodeId,
        meshGateway = meshService,
        conversationStore = conversationStore,
        securityCodec = PassthroughSecurityCodec()
    )

    // ── Port constants ────────────────────────────────────────────────────────

    companion object {
        /** UDP broadcast port for HELLO / RREQ / RERR packets. */
        const val UDP_PORT = 49152

        /** TCP port for unicast MESSAGE / RREP / ACK packets. */
        const val TCP_PORT = 49153
    }
}
