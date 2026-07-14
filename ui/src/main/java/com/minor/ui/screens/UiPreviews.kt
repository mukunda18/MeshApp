package com.minor.ui.screens // Moved namespace to screens to make imports cleaner

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.minor.ui.theme.MeshAppTheme
import com.minor.ui.screens.home.HomeScreenContent
import com.minor.ui.screens.profile.ProfileScreen
import com.minor.ui.screens.conversation.ConversationScreen
import com.minor.ui.screens.about.AboutScreen
import com.minor.ui.state.HomeUiState

@Composable
fun PreviewWrapper(content: @Composable () -> Unit) {
    MeshAppTheme {
        content()
    }
}

@Preview(name = "Home - Mesh Online", showBackground = true, showSystemUi = true)
@Composable
fun PreviewHomeScreenOnline() {
    // We create a mock State object without relying on ViewModels!
    val mockOnlineState = HomeUiState(
        appName = "Mesh App",
        isMeshOn = true,
        connectionStatus = "Connected to 3 local nodes"
    )

    PreviewWrapper {
        HomeScreenContent(
            uiState = mockOnlineState,
            onToggleMesh = {},
            onRefreshNetwork = {},
            onNavigateToNearbyNodes = {},
            onNavigateToNetworkInterfaces = {},
            onNavigateToProfile = {},
            onNavigateToAbout = {}
        )
    }
}

@Preview(name = "Home - Mesh Offline", showBackground = true, showSystemUi = true)
@Composable
fun PreviewHomeScreenOffline() {
    val mockOfflineState = HomeUiState(
        appName = "Mesh App",
        isMeshOn = false,
        connectionStatus = "Mesh Offline"
    )

    PreviewWrapper {
        HomeScreenContent(
            uiState = mockOfflineState,
            onToggleMesh = {},
            onRefreshNetwork = {},
            onNavigateToNearbyNodes = {},
            onNavigateToNetworkInterfaces = {},
            onNavigateToProfile = {},
            onNavigateToAbout = {}
        )
    }
}

@Preview(name = "Profile Screen", showBackground = true, showSystemUi = true)
@Composable
fun PreviewProfileScreen() {
    PreviewWrapper {
        // Supplying mandatory arguments to prevent compile errors
        ProfileScreen(
            onBack = {},
            onNavigateHome = {},
            onNavigateChats = {},
            onNavigateNearbyNodes = {},
            onNavigateNetworkInterfaces = {},
            onNavigateAbout = {}
        )
    }
}

@Preview(name = "Conversation Screen", showBackground = true, showSystemUi = true)
@Composable
fun PreviewConversationScreen() {
    PreviewWrapper {
        ConversationScreen(
            nodeId = "test-node-id",
            onBack = {}
        )
    }
}

@Preview(name = "About Screen", showBackground = true, showSystemUi = true)
@Composable
fun PreviewAboutScreen() {
    PreviewWrapper {
        AboutScreen(
            onBack = {}
        )
    }
}