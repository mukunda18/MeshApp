package com.meshapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meshapp.meshcontrol.MeshService
import com.meshapp.messaging.MessagingService
import com.meshapp.filetransfer.FileTransferService
import com.meshapp.model.NodeId
import com.meshapp.voice.VoiceCallManager

class ConversationViewModelFactory(
    private val ownNodeId: NodeId,
    private val messagingService: MessagingService,
    private val meshService: MeshService,
    private val voiceCallManager: VoiceCallManager,
    private val fileTransferService: FileTransferService
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConversationViewModel(
                ownNodeId = ownNodeId,
                messagingService = messagingService,
                meshService = meshService,
                voiceCallManager = voiceCallManager,
                fileTransferService = fileTransferService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
