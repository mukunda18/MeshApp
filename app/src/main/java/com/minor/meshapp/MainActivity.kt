package com.minor.meshapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.minor.ui.navigation.MeshAppNavHost
import com.minor.ui.viewmodel.ChatsViewModelFactory
import com.minor.ui.viewmodel.ConversationViewModelFactory
import com.minor.ui.viewmodel.HomeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val meshApp = application as MeshApplication
        val homeViewModelFactory = HomeViewModelFactory(
            application = meshApp,
            meshService = meshApp.container.meshService,
            appName = getString(R.string.app_name),
            deviceName = meshApp.container.identity.displayName
        )
        val chatsViewModelFactory = ChatsViewModelFactory(
            messagingService = meshApp.container.messagingService,
            meshService = meshApp.container.meshService
        )
        val conversationViewModelFactory = ConversationViewModelFactory(
            ownNodeId = meshApp.container.identity.nodeId,
            messagingService = meshApp.container.messagingService,
            meshService = meshApp.container.meshService
        )

        setContent {
            MeshAppNavHost(
                homeViewModelFactory = homeViewModelFactory,
                chatsViewModelFactory = chatsViewModelFactory,
                conversationViewModelFactory = conversationViewModelFactory
            )
        }
    }
}
