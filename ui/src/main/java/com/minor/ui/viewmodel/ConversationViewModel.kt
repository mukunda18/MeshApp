package com.minor.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.minor.ui.fake.FakeDataProvider
import com.minor.ui.state.ConversationMessageUiState
import com.minor.ui.state.ConversationUiState
import com.minor.ui.state.NodeCardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConversationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationUiState(node = NodeCardState("", "", false, "")))

    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    fun initialize(node: NodeCardState) {
        _uiState.value = ConversationUiState(
            node = node,
            messages = FakeDataProvider.messagesByNodeId[node.id].orEmpty()
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val current = _uiState.value
        val newMessage = ConversationMessageUiState(
            id = "local-${System.currentTimeMillis()}",
            text = text.trim(),
            isOutgoing = true,
            timestamp = "Now"
        )
        _uiState.value = current.copy(messages = current.messages + newMessage)
    }
}
