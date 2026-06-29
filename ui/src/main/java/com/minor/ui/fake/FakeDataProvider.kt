package com.minor.ui.fake

/*
Temporary fake data.

Replace with backend implementation later.
UI should require minimal changes.
*/

import com.minor.ui.state.ConversationMessageUiState
import com.minor.ui.state.NetworkInterfaceUiState
import com.minor.ui.state.NetworkInterfacesUiState
import com.minor.ui.state.NodeCardState
import com.minor.ui.state.ProfileUiState

object FakeDataProvider {
    val profile: ProfileUiState = ProfileUiState(
        name = "Avery",
        avatarInitials = "AV"
    )

    val meshStatus: Boolean = true

    val networkInterfaces: NetworkInterfacesUiState = NetworkInterfacesUiState(
        isStaApSupported = false,
        isLikelySupported = true,
        interfaces = listOf(
            NetworkInterfaceUiState(
                interfaceName = "wlan0",
                localIp = "192.168.1.24",
                broadcastIp = "192.168.1.255"
            ),
            NetworkInterfaceUiState(
                interfaceName = "ap0",
                localIp = "192.168.43.1",
                broadcastIp = "192.168.43.255"
            )
        )
    )

    val nodes: List<NodeCardState> = listOf(
        NodeCardState(
            id = "node-alpha",
            name = "Node Alpha",
            isOnline = true,
            avatarInitials = "AL"
        ),
        NodeCardState(
            id = "node-beta",
            name = "Node Beta",
            isOnline = false,
            avatarInitials = "BT"
        ),
        NodeCardState(
            id = "node-gamma",
            name = "Node Gamma",
            isOnline = true,
            avatarInitials = "GM"
        )
    )

    val messagesByNodeId: Map<String, List<ConversationMessageUiState>> = mapOf(
        "node-alpha" to listOf(
            ConversationMessageUiState(
                id = "m1",
                text = "Hello from the mesh network.",
                isOutgoing = false,
                timestamp = "09:41"
            ),
            ConversationMessageUiState(
                id = "m2",
                text = "I can share updates instantly.",
                isOutgoing = true,
                timestamp = "09:42"
            )
        ),
        "node-beta" to listOf(
            ConversationMessageUiState(
                id = "m3",
                text = "I’m offline for now.",
                isOutgoing = false,
                timestamp = "08:20"
            )
        ),
        "node-gamma" to listOf(
            ConversationMessageUiState(
                id = "m4",
                text = "Ready when you are.",
                isOutgoing = false,
                timestamp = "10:05"
            )
        )
    )
}
