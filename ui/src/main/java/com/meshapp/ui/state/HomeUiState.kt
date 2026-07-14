package com.meshapp.ui.state

import com.meshapp.messaging.MessageDeliveryStatus

data class HomeUiState(
    val isMeshOn: Boolean = false,
    val profile: ProfileUiState = ProfileUiState(),
    val appName: String = "Mesh App",
    val meshStatusLabel: String = "STOPPED",
    val connectionStatus: String = "No nearby nodes",
    val isStaApSupported: Boolean = false,
    val isStaApLikelySupported: Boolean = false,
    val networkInterfaceCount: Int = 0,
    val connectedNodes: List<HomeNodeUiState> = emptyList()
)

data class HomeNodeUiState(
    val nodeId: String,
    val name: String,
    val avatarInitials: String,
    val isOnline: Boolean,
    val status: String,
    val ip: String?,
    val hopCount: Int?
)

data class ProfileUiState(
    val name: String = "Guest",
    val nodeId: String = "Unavailable",
    val avatarInitials: String = "G"
)

data class NodeCardState(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val avatarInitials: String,
    val lastMessagePreview: String? = null,
    val lastMessageTimestamp: String? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false
)

data class ConversationUiState(
    val node: NodeCardState,
    val messages: List<ConversationMessageUiState> = emptyList()
)

data class ConversationMessageUiState(
    val id: String,
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: String,
    val deliveryStatusLabel: String? = null,
    val deliveryStatus: MessageDeliveryStatus? = null
)
