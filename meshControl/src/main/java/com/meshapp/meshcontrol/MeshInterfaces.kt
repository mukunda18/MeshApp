package com.meshapp.meshcontrol

import com.meshapp.model.NodeId
import com.meshapp.model.PublicKey
import com.meshapp.model.MessageId
import com.meshapp.network.TCPReceiver
import com.meshapp.network.TCPSender
import com.meshapp.network.UdpSocket
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

data class DeliveryStatus(val messageId: MessageId, val state: DeliveryState)

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
