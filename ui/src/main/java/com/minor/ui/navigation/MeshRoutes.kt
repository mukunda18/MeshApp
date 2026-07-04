package com.minor.ui.navigation

object MeshRoutes {
    const val HOME = "home"
    const val CHATS = "chats"
    const val CONVERSATION = "conversation/{nodeId}"
    const val NETWORK_INTERFACES = "NetworkInterfaces"

    fun conversation(nodeId: String): String = "conversation/$nodeId"
}
