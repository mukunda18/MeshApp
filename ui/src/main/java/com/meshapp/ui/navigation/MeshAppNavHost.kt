package com.meshapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meshapp.ui.components.BottomNavigationBar
import com.meshapp.ui.screens.about.AboutScreen
import com.meshapp.ui.screens.chats.ChatsScreen
import com.meshapp.ui.screens.conversation.ConversationScreen
import com.meshapp.ui.screens.home.HomeScreen
import com.meshapp.logger.LogScreen
import com.meshapp.ui.screens.nearbynodes.NearbyNodesScreen
import com.meshapp.ui.screens.networkinterfaces.NetworkInterfacesScreen
import com.meshapp.ui.screens.profile.ProfileScreen
import com.meshapp.ui.screens.voice.VoiceCallOverlay
import com.meshapp.ui.theme.MeshAppTheme
import com.meshapp.ui.viewmodel.ChatsViewModel
import com.meshapp.ui.viewmodel.ConversationViewModel
import com.meshapp.ui.viewmodel.HomeViewModel
import com.meshapp.voice.CallState

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

        Scaffold(
            bottomBar = {
                if (currentRoute in listOf(MeshRoutes.HOME, MeshRoutes.CHATS) && uiState.voiceCallState is CallState.Idle) {
                    BottomNavigationBar(currentRoute = currentRoute, onNavigate = { route ->
                        when (route) {
                            MeshRoutes.HOME -> navController.navigate(MeshRoutes.HOME) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            MeshRoutes.CHATS -> navController.navigate(MeshRoutes.CHATS) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    })
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = MeshRoutes.HOME,
                    modifier = Modifier.padding(paddingValues)
                ) {
                    composable(MeshRoutes.HOME) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToNearbyNodes = { navController.navigate(MeshRoutes.NEARBY_NODES) },
                            onNavigateToNetworkInterfaces = { navController.navigate(MeshRoutes.NETWORK_INTERFACES) },
                            onNavigateToProfile = { navController.navigate(MeshRoutes.PROFILE) },
                            onNavigateToAbout = { navController.navigate(MeshRoutes.ABOUT) },
                            onNavigateToLogs = { navController.navigate(MeshRoutes.LOGS) }
                        )
                    }
                    composable(MeshRoutes.LOGS) {
                        LogScreen(onBack = { navController.popBackStack() })
                    }
                    composable(MeshRoutes.PROFILE) {
                        ProfileScreen(
                            viewModel = homeViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateHome = {
                                navController.navigate(MeshRoutes.HOME) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateChats = {
                                navController.navigate(MeshRoutes.CHATS) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateNearbyNodes = { navController.navigate(MeshRoutes.NEARBY_NODES) },
                            onNavigateNetworkInterfaces = { navController.navigate(MeshRoutes.NETWORK_INTERFACES) },
                            onNavigateAbout = { navController.navigate(MeshRoutes.ABOUT) }
                        )
                    }
                    composable(MeshRoutes.NEARBY_NODES) {
                        NearbyNodesScreen(
                            viewModel = homeViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateHome = {
                                navController.navigate(MeshRoutes.HOME) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateChats = {
                                navController.navigate(MeshRoutes.CHATS) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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

                VoiceCallOverlay(
                    state = uiState.voiceCallState,
                    isMinimized = uiState.isCallMinimized,
                    onAccept = { homeViewModel.acceptCall() },
                    onReject = { homeViewModel.rejectCall() },
                    onCancel = { homeViewModel.cancelCall() },
                    onHangup = { homeViewModel.hangupCall() },
                    onMinimize = { homeViewModel.minimizeCall() }
                )

                if (uiState.isCallMinimized && uiState.voiceCallState !is CallState.Idle) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF29DC67))
                            .clickable { homeViewModel.maximizeCall() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Back to Call",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
