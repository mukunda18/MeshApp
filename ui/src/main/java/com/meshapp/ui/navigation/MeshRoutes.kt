package com.meshapp.ui.navigation

import android.net.Uri

object MeshRoutes {
    const val HOME = "home"
    const val CHATS = "chats"
    const val PROFILE = "profile"
    const val ABOUT = "about"
    const val LOGS = "logs"
    const val CONVERSATION = "conversation/{nodeId}"
    const val NETWORK_INTERFACES = "NetworkInterfaces"

    fun conversation(nodeId: String, name: String? = null): String {
        val encodedName = name?.trim()?.takeIf { it.isNotEmpty() }?.let { Uri.encode(it) }
        return if (encodedName != null) {
            "conversation/$nodeId?name=$encodedName"
        } else {
            "conversation/$nodeId"
        }
    }
}
