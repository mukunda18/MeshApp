package com.meshapp.ui.navigation

object MeshRoutes {
    const val HOME = "home"
    const val CHATS = "chats"
    const val PROFILE = "profile"
    const val NEARBY_NODES = "nearby_nodes"
    const val ABOUT = "about"
    const val LOGS = "logs"
    const val CONVERSATION = "conversation/{nodeId}"
    const val NETWORK_INTERFACES = "NetworkInterfaces"

    fun conversation(nodeId: String): String = "conversation/$nodeId"
}
