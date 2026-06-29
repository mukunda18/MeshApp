package com.minor.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.minor.ui.fake.FakeDataProvider
import com.minor.ui.state.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            isMeshOn = FakeDataProvider.meshStatus,
            profile = FakeDataProvider.profile
        )
    )

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun toggleMesh() {
        _uiState.value = _uiState.value.copy(isMeshOn = !_uiState.value.isMeshOn)
    }
}
