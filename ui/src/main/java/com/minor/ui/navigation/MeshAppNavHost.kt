package com.minor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.minor.ui.components.BottomNavigationBar
import com.minor.ui.screens.chats.ChatsScreen
import com.minor.ui.screens.conversation.ConversationScreen
import com.minor.ui.screens.home.HomeScreen
import com.minor.ui.screens.networkinterfaces.NetworkInterfacesScreen
import com.minor.ui.theme.MeshAppTheme

@Composable
fun MeshAppNavHost() {
    MeshAppTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route?.substringBefore("/") ?: MeshRoutes.HOME

        Scaffold(
            bottomBar = {
                if (currentRoute in listOf(MeshRoutes.HOME, MeshRoutes.CHATS)) {
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
            NavHost(
                navController = navController,
                startDestination = MeshRoutes.HOME,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(MeshRoutes.HOME) {
                    HomeScreen(
                        viewModel = viewModel(),
                        onNavigateToChats = { navController.navigate(MeshRoutes.CHATS) },
                        onNavigateToNetworkInterfaces = { navController.navigate(MeshRoutes.NETWORK_INTERFACES) }
                    )
                }
                composable(MeshRoutes.CHATS) {
                    ChatsScreen(
                        viewModel = viewModel(),
                        onNodeClick = { node -> navController.navigate(MeshRoutes.conversation(node.id)) }
                    )
                }
                composable(MeshRoutes.NETWORK_INTERFACES) {
                    NetworkInterfacesScreen(
                        viewModel = viewModel(),
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = MeshRoutes.CONVERSATION,
                    arguments = listOf(navArgument("nodeId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val nodeId = backStackEntry.arguments?.getString("nodeId") ?: ""
                    ConversationScreen(
                        viewModel = viewModel(),
                        nodeId = nodeId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
