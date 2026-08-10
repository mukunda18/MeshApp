package com.meshapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meshapp.meshcontrol.MeshService
import com.meshapp.messaging.MessagingService
import com.meshapp.security.NodesStore
import com.meshapp.voice.VoiceCallManager
import com.meshapp.model.NodeId

class ChatsViewModelFactory(
    private val messagingService: MessagingService,
    private val meshService: MeshService,
    private val voiceCallManager: VoiceCallManager,
    private val ownNodeId: NodeId
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatsViewModel(
                messagingService = messagingService,
                meshService = meshService,
                voiceCallManager = voiceCallManager,
                ownNodeId = ownNodeId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
