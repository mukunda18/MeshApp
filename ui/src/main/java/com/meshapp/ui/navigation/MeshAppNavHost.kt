package com.meshapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meshapp.logger.LogScreen
import com.meshapp.ui.components.BottomNavigationBar
import com.meshapp.ui.components.ProfileAvatar
import com.meshapp.ui.screens.about.AboutScreen
import com.meshapp.ui.screens.chats.ChatsScreen
import com.meshapp.ui.screens.conversation.ConversationScreen
import com.meshapp.ui.screens.home.HomeScreen
import com.meshapp.ui.screens.nearbynodes.NearbyNodesScreen
import com.meshapp.ui.screens.networkinterfaces.NetworkInterfacesScreen
import com.meshapp.ui.screens.profile.ProfileScreen
import com.meshapp.ui.screens.voice.VoiceCallOverlay
import com.meshapp.ui.theme.MeshAppTheme
import com.meshapp.ui.theme.MeshBg0
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenOnAccent
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.viewmodel.ChatsViewModel
import com.meshapp.ui.viewmodel.ConversationViewModel
import com.meshapp.ui.viewmodel.HomeViewModel
import com.meshapp.voice.CallState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshAppNavHost(
    homeViewModelFactory: ViewModelProvider.Factory? = null,
    chatsViewModelFactory: ViewModelProvider.Factory? = null,
    conversationViewModelFactory: ViewModelProvider.Factory? = null
) {
    MeshAppTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()

        val currentRoute = backStackEntry?.destination?.route?.substringBefore("/") ?: MeshRoutes.HOME

        val homeViewModel: HomeViewModel = if (homeViewModelFactory != null) {
            viewModel(factory = homeViewModelFactory)
        } else {
            viewModel()
        }
        val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

        val isRootTab = currentRoute in listOf(MeshRoutes.HOME, MeshRoutes.CHATS)

        var showQuickActions by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = MeshBg0,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = getTopBarTitle(currentRoute),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MeshTextPrimary
                        )
                    },
                    navigationIcon = {
                        if (!isRootTab) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Navigate Back",
                                    tint = MeshTextPrimary
                                )
                            }
                        }
                    },
                    actions = {
                        if (isRootTab) {
                            Box {
                                IconButton(onClick = { showQuickActions = true }) {
                                    ProfileAvatar(
                                        initials = uiState.profile.avatarInitials,
                                        size = 32.dp
                                    )
                                }
                                DropdownMenu(
                                    expanded = showQuickActions,
                                    onDismissRequest = { showQuickActions = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("My Node Profile") },
                                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                        onClick = {
                                            showQuickActions = false
                                            navController.navigate(MeshRoutes.PROFILE)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Nearby Nodes") },
                                        leadingIcon = { Icon(Icons.Outlined.Devices, contentDescription = null) },
                                        onClick = {
                                            showQuickActions = false
                                            navController.navigate(MeshRoutes.NEARBY_NODES)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Network Interfaces") },
                                        leadingIcon = { Icon(Icons.Outlined.Router, contentDescription = null) },
                                        onClick = {
                                            showQuickActions = false
                                            navController.navigate(MeshRoutes.NETWORK_INTERFACES)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("System Logs") },
                                        leadingIcon = { Icon(Icons.Outlined.List, contentDescription = null) },
                                        onClick = {
                                            showQuickActions = false
                                            navController.navigate(MeshRoutes.LOGS)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("About Mesh") },
                                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                        onClick = {
                                            showQuickActions = false
                                            navController.navigate(MeshRoutes.ABOUT)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MeshBg0
                    )
                )
            },
            bottomBar = {
                if (isRootTab && uiState.voiceCallState is CallState.Idle) {
                    BottomNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route -> navController.navigateToTab(route) }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = MeshRoutes.HOME,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(MeshRoutes.HOME) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToNearbyNodes = { navController.navigate(MeshRoutes.NEARBY_NODES) }
                        )
                    }
                    composable(MeshRoutes.LOGS) {
                        LogScreen(onBack = { navController.popBackStack() })
                    }
                    composable(MeshRoutes.PROFILE) {
                        ProfileScreen(
                            viewModel = homeViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateHome = { navController.navigateToTab(MeshRoutes.HOME) },
                            onNavigateChats = { navController.navigateToTab(MeshRoutes.CHATS) },
                            onNavigateNearbyNodes = { navController.navigate(MeshRoutes.NEARBY_NODES) },
                            onNavigateNetworkInterfaces = { navController.navigate(MeshRoutes.NETWORK_INTERFACES) },
                            onNavigateAbout = { navController.navigate(MeshRoutes.ABOUT) }
                        )
                    }
                    composable(MeshRoutes.NEARBY_NODES) {
                        NearbyNodesScreen(
                            viewModel = homeViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateHome = { navController.navigateToTab(MeshRoutes.HOME) },
                            onNavigateChats = { navController.navigateToTab(MeshRoutes.CHATS) },
                            onNodeClick = { nodeId -> navController.navigate(MeshRoutes.conversation(nodeId)) }
                        )
                    }
                    composable(MeshRoutes.CHATS) {
                        val chatsViewModel: ChatsViewModel = if (chatsViewModelFactory != null) {
                            viewModel(factory = chatsViewModelFactory)
                        } else {
                            viewModel()
                        }
                        ChatsScreen(
                            viewModel = chatsViewModel,
                            onNodeClick = { node -> navController.navigate(MeshRoutes.conversation(node.id)) }
                        )
                    }
                    composable(MeshRoutes.NETWORK_INTERFACES) {
                        NetworkInterfacesScreen(
                            viewModel = viewModel(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(MeshRoutes.ABOUT) {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = MeshRoutes.CONVERSATION,
                        arguments = listOf(navArgument("nodeId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val nodeId = backStackEntry.arguments?.getString("nodeId") ?: ""
                        val conversationViewModel: ConversationViewModel = if (conversationViewModelFactory != null) {
                            viewModel(factory = conversationViewModelFactory)
                        } else {
                            viewModel()
                        }
                        ConversationScreen(
                            viewModel = conversationViewModel,
                            nodeId = nodeId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Active Voice Call Overlay
                VoiceCallOverlay(
                    state = uiState.voiceCallState,
                    isMinimized = uiState.isCallMinimized,
                    onAccept = { homeViewModel.acceptCall() },
                    onReject = { homeViewModel.rejectCall() },
                    onCancel = { homeViewModel.cancelCall() },
                    onHangup = { homeViewModel.hangupCall() },
                    onMinimize = { homeViewModel.minimizeCall() }
                )

                // Minimized Call Return Pill
                if (uiState.isCallMinimized && uiState.voiceCallState !is CallState.Idle) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = MeshSpacing.xs)
                            .clip(MeshShapes.chip)
                            .background(MeshGreen)
                            .clickable { homeViewModel.maximizeCall() }
                            .padding(horizontal = MeshSpacing.md, vertical = MeshSpacing.xs)
                    ) {
                        Text(
                            text = "Back to Call",
                            color = MeshGreenOnAccent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun getTopBarTitle(route: String): String {
    return when (route) {
        MeshRoutes.HOME -> "Mesh Network"
        MeshRoutes.CHATS -> "Conversations"
        MeshRoutes.NEARBY_NODES -> "Nearby Nodes"
        MeshRoutes.NETWORK_INTERFACES -> "Network Interfaces"
        MeshRoutes.PROFILE -> "Node Profile"
        MeshRoutes.ABOUT -> "About Mesh"
        MeshRoutes.LOGS -> "System Logs"
        MeshRoutes.CONVERSATION -> "Chat"
        else -> "Mesh"
    }
}