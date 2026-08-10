package com.meshapp.meshapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.meshapp.ui.navigation.MeshAppNavHost
import com.meshapp.ui.viewmodel.ChatsViewModelFactory
import com.meshapp.ui.viewmodel.ConversationViewModelFactory
import com.meshapp.ui.viewmodel.HomeViewModelFactory
import com.meshapp.ui.viewmodel.MeshController

class MainActivity : ComponentActivity() {

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    private class AppFactories(
        val home: HomeViewModelFactory,
        val chats: ChatsViewModelFactory,
        val conversation: ConversationViewModelFactory
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ requires runtime permission to post notifications
        // Voice simulation requires RECORD_AUDIO
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            requestPermissions.launch(permissions.toTypedArray())
        }

        val meshApp = application as MeshApplication

        // Bridges the UI toggle to the foreground service (Option 1).
        val meshController = object : MeshController {
            override fun start() = MeshForegroundService.start(applicationContext)
            override fun stop() = MeshForegroundService.stop(applicationContext)
            override fun startVoiceSim() = MeshForegroundService.startVoiceSim(applicationContext)
            override fun stopVoiceSim() = MeshForegroundService.stopVoiceSim(applicationContext)
            override val isVoiceSimActive = MeshForegroundService.isVoiceSimActive
        }

        setContent {
            // The DI container is built off the main thread; await it without blocking.
            val factories by produceState<AppFactories?>(initialValue = null) {
                try {
                    val container = meshApp.awaitContainer()
                    value = AppFactories(
                        home = HomeViewModelFactory(
                            application = meshApp,
                            meshService = container.meshService,
                            meshController = meshController,
                            voiceCallManager = container.voiceCallManager,
                            identityManager = container.identityManager,
                            appName = getString(R.string.app_name),
                            deviceName = container.identity.name,
                            nodeId = container.identity.nodeId.toString()
                        ),
                        chats = ChatsViewModelFactory(
                            messagingService = container.messagingService,
                            meshService = container.meshService,
                            nodesStore = container.nodesStore,
                            voiceCallManager = container.voiceCallManager,
                            ownNodeId = container.identity.nodeId
                        ),
                        conversation = ConversationViewModelFactory(
                            ownNodeId = container.identity.nodeId,
                            messagingService = container.messagingService,
                            meshService = container.meshService,
                            voiceCallManager = container.voiceCallManager,
                            fileTransferService = container.fileTransferService,
                            voiceMessageRecorder = container.voiceMessageRecorder,
                            voiceMessagePlayer = container.voiceMessagePlayer
                        )
                    )
                } catch (e: Exception) {
                    // Handle initialization failure. Possible causes:
                    // - SQLiteException (database corruption or storage full)
                    // - KeyStoreException (device-specific hardware key issues)
                    android.util.Log.e("MainActivity", "Critical failure during app initialization", e)
                    // In a real app, we would show an error screen here.
                }
            }

            val ready = factories
            if (ready == null) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                MeshAppNavHost(
                    homeViewModelFactory = ready.home,
                    chatsViewModelFactory = ready.chats,
                    conversationViewModelFactory = ready.conversation
                )
            }
        }
    }
}