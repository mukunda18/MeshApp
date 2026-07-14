package com.meshapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meshapp.meshcontrol.MeshService
import com.meshapp.messaging.MessagingService
import com.meshapp.model.NodeId

class ConversationViewModelFactory(
    private val ownNodeId: NodeId,
    private val messagingService: MessagingService,
    private val meshService: MeshService
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConversationViewModel(
                ownNodeId = ownNodeId,
                messagingService = messagingService,
                meshService = meshService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
