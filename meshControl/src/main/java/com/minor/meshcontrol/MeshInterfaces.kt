package com.minor.meshcontrol

import com.minor.model.NodeId
import com.minor.model.PublicKey
import com.minor.network.TCPReceiver
import com.minor.network.TCPSender
import com.minor.network.UdpSocket
import kotlinx.coroutines.CoroutineScope

data class MeshSockets(
    val tcpReceiver: TCPReceiver,
    val tcpSender: TCPSender,
    val udpSocket: UdpSocket,
)

fun interface MeshSocketFactory {
    fun create(scope: CoroutineScope, config: MeshConfig): MeshSockets
}

enum class MeshState {
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR
}

data class DeliveryStatus(val messageId: Long, val state: DeliveryState)

enum class DeliveryState {
    SENT,
    DELIVERED,
    FAILED
}

data class PeerState(
    val nodeId: NodeId,
    val ip: String?,
    val name: String?,
    val publicKey: PublicKey?,
    val status: PeerStatus,
    val lastSeen: Long?
)

enum class PeerStatus {
    ACTIVE,
    REMOVED
}
