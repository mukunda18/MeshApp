package com.minor.ui.state

data class HomeUiState(
    val isMeshOn: Boolean = false,
    val profile: ProfileUiState = ProfileUiState()
)

data class ProfileUiState(
    val name: String = "Guest",
    val avatarInitials: String = "G"
)

data class NodeCardState(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val avatarInitials: String
)

data class ConversationUiState(
    val node: NodeCardState,
    val messages: List<ConversationMessageUiState> = emptyList()
)

data class ConversationMessageUiState(
    val id: String,
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: String
)
