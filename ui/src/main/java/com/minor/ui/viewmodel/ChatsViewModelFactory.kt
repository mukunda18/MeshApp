package com.minor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.minor.meshcontrol.MeshService
import com.minor.messaging.MessagingService

class ChatsViewModelFactory(
    private val messagingService: MessagingService,
    private val meshService: MeshService
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatsViewModel(
                messagingService = messagingService,
                meshService = meshService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
