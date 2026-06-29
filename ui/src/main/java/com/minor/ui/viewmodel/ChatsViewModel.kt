package com.minor.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.minor.ui.fake.FakeDataProvider
import com.minor.ui.state.NodeCardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        ChatsUiState(nodes = FakeDataProvider.nodes)
    )

    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()
}

data class ChatsUiState(
    val nodes: List<NodeCardState> = emptyList()
)
