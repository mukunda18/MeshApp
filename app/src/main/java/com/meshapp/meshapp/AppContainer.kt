package com.meshapp.meshapp

import android.content.Context
import android.os.Build
import com.meshapp.meshapp.network.AndroidMeshSocketFactory
import com.meshapp.meshcontrol.AudioController
import com.meshapp.meshcontrol.MeshConfig
import com.meshapp.meshcontrol.MeshService
import com.meshapp.messaging.ConversationStore
import com.meshapp.messaging.MessagingService
import com.meshapp.filetransfer.FileTransferService
import com.meshapp.voice.VoiceCallManager
import com.meshapp.voicemessage.VoiceMessagePlayer
import com.meshapp.voicemessage.VoiceMessageRecorder
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
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    // ── Audio control ─────────────────────────────────────────────────────────
    // Created first to ensure the central audio manager is ready for all services.
    val audioController: AudioController = AudioController(appContext)

    // ── Identity & security ───────────────────────────────────────────────────

    val nodesStore: NodesStore = try {
        SqlNodesStore(appContext)
    } catch (e: Exception) {
        throw RuntimeException("Failed to initialize SqlNodesStore", e)
    }

    // IdentityManager uses PersistentIdentityStore for encrypted SharedPreferences storage.
    val identityManager: IdentityManager = IdentityManager(PersistentIdentityStore(appContext))

    val identity: Identity = try {
        // Sanitize Build.MODEL for use as the default node name.
        identityManager.getOrGenerate(
            Build.MODEL
                .replace(Regex("[^a-zA-Z0-9 _-]"), "")
                .trim()
                .take(20)
                .ifBlank { "MeshUser" }
        )
    } catch (e: Exception) {
        throw RuntimeException("Failed to get or generate identity", e)
    }

    val security: Security = try {
        Security(identity, nodesStore)
    } catch (e: Exception) {
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
        audioController = audioController, // Pass standardized audio controller
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

    val voiceCallManager: VoiceCallManager = VoiceCallManager(
        context = appContext,
        messagingService = messagingService,
        meshService = meshService,
        config = meshConfig,
        audioController = audioController // Pass standardized audio controller
    )

    val fileTransferService: FileTransferService = FileTransferService(
        context = appContext,
        ownNodeId = identity.nodeId,
        meshService = meshService,
        messagingService = messagingService
    )

    // ── Voice messaging ───────────────────────────────────────────────────────

    val voiceMessageRecorder: VoiceMessageRecorder = VoiceMessageRecorder(
        context = appContext,
        audioController = audioController
    )

    val voiceMessagePlayer: VoiceMessagePlayer = VoiceMessagePlayer(
        audioController = audioController,
        settings = meshConfig.audioConfig.messageSettings // Pass feature-specific settings
    )

    // ── Port constants ────────────────────────────────────────────────────────

    companion object {
        const val UDP_PORT = 49152
        const val TCP_PORT = 49153
    }
}
