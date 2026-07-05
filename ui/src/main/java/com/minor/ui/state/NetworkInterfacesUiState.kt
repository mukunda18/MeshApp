package com.minor.ui.state

data class NetworkInterfacesUiState(
    val isStaApSupported: Boolean = false,
    val isLikelySupported: Boolean = false,
    val interfaces: List<NetworkInterfaceUiState> = emptyList()
)

data class NetworkInterfaceUiState(
    val interfaceName: String,
    val localIp: String,
    val broadcastIp: String
)
