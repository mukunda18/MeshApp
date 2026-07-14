package com.meshapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshapp.network.NetworkInfo
import com.meshapp.network.NetworkScanner
import com.meshapp.ui.state.NetworkInterfaceUiState
import com.meshapp.ui.state.NetworkInterfacesUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class NetworkInterfacesViewModel(application: Application) : AndroidViewModel(application) {
    private val networkInfo = NetworkInfo(application)
    private val _uiState = MutableStateFlow(
        NetworkInterfacesUiState(
            isStaApSupported = networkInfo.isStaApSupported(),
            isLikelySupported = networkInfo.isLikelySupported(),
            interfaces = emptyList()
        )
    )

    val uiState: StateFlow<NetworkInterfacesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val scannedInterfaces = NetworkScanner.getNetworkInterfaceInfo().map {
                    NetworkInterfaceUiState(
                        interfaceName = it.interfaceName,
                        localIp = it.localAddress.hostAddress ?: "",
                        broadcastIp = it.broadcastAddress.hostAddress ?: ""
                    )
                }
                _uiState.value = _uiState.value.copy(interfaces = scannedInterfaces)
                delay(2000.milliseconds)
            }
        }
    }
}
