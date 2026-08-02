package com.meshapp.voice

import com.meshapp.model.MessageId
import com.meshapp.model.NodeId
import com.meshapp.model.Timestamp

sealed interface CallState {
    data object Idle : CallState
    
    data class Dialing(
        val peerNodeId: NodeId,
        val callId: MessageId,
        val startTime: Timestamp
    ) : CallState
    
    data class Ringing(
        val peerNodeId: NodeId,
        val callId: MessageId,
        val startTime: Timestamp,
        val peerPublicKey: ByteArray
    ) : CallState
    
    data class Active(
        val peerNodeId: NodeId,
        val callId: MessageId,
        val startTime: Timestamp,
        val connectedTime: Timestamp,
        val callCrypto: CallCrypto
    ) : CallState
    
    data class Ended(
        val peerNodeId: NodeId,
        val reason: String
    ) : CallState
}
