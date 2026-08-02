package com.meshapp.voice

import android.content.Context
import com.meshapp.logger.MeshLogger
import com.meshapp.meshcontrol.MeshConfig
import com.meshapp.meshcontrol.MeshService
import com.meshapp.messaging.MessagingService
import com.meshapp.model.CallAccept
import com.meshapp.model.CallOffer
import com.meshapp.model.CallSignal
import com.meshapp.model.CallSignalProtocol
import com.meshapp.model.CallSignalType
import com.meshapp.model.MessageId
import com.meshapp.model.NodeId
import com.meshapp.model.Timestamp
import com.meshapp.model.randomMessageId
import com.meshapp.routing.RouteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import kotlin.time.Duration.Companion.milliseconds

class VoiceCallManager(
    private val context: Context,
    private val messagingService: MessagingService,
    private val meshService: MeshService,
    private val config: MeshConfig,
    private val ownNodeId: NodeId
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState = _callState.asStateFlow()

    private var voiceSession: VoiceSessionManager? = null

    // Ephemeral ECDH key pair for this call. The public key is sent in CallOffer/CallAccept
    // and used to derive the per-call AES-GCM key via CallCrypto.
    private val ephemeralKeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(256)
    }.generateKeyPair()
    private val ownEphemeralPublicKey: ByteArray = ephemeralKeyPair.public.encoded
    private val ownEphemeralPrivateKey: ByteArray = ephemeralKeyPair.private.encoded

    init {
        scope.launch {
            messagingService.callSignalsStream.collect { (sourceNodeId, signal) ->
                handleIncomingSignal(sourceNodeId, signal)
            }
        }
        
        scope.launch {
            meshService.routeEventsStream.collect { event ->
                if (event is RouteEvent.Invalidated) {
                    val peerNodeId = when (val current = _callState.value) {
                        is CallState.Dialing -> current.peerNodeId
                        is CallState.Ringing -> current.peerNodeId
                        is CallState.Active -> current.peerNodeId
                        else -> null
                    }
                    if (peerNodeId != null && peerNodeId.toString() == event.nodeId.toString()) {
                        MeshLogger.info("VoiceCallManager", "Route to peer lost: $peerNodeId")
                        endCallWithReason("Connection lost")
                    }
                }
            }
        }
    }

    fun dial(peerNodeId: NodeId) {
        if (_callState.value !is CallState.Idle) return

        val callId = randomMessageId()
        val offer = CallOffer(ownEphemeralPublicKey)
        val buf = ByteArray(256)
        val size = CallSignalProtocol.callOffer.write(buf, offer, 0)

        val signal = CallSignal(callId, CallSignalType.OFFER, buf.copyOfRange(0, size))

        _callState.value = CallState.Dialing(peerNodeId, callId, Timestamp(System.currentTimeMillis()))
        messagingService.sendCallSignal(peerNodeId, signal)

        MeshLogger.info("VoiceCallManager", "Dialing $peerNodeId")

        // Start dialing timeout
        scope.launch {
            delay(config.callDialingTimeoutMs.milliseconds)
            val current = _callState.value
            if (current is CallState.Dialing && current.callId == callId) {
                MeshLogger.info("VoiceCallManager", "Dialing timeout for $peerNodeId")
                cancel()
                endCallWithReason("No answer")
            }
        }
    }

    fun accept() {
        val current = _callState.value
        if (current !is CallState.Ringing) return

        val accept = CallAccept(ownEphemeralPublicKey)
        val buf = ByteArray(256)
        val size = CallSignalProtocol.callAccept.write(buf, accept, 0)

        val signal = CallSignal(current.callId, CallSignalType.ACCEPT, buf.copyOfRange(0, size))

        val callCrypto = CallCrypto(
            ownEphemeralPrivateKeyBytes = ownEphemeralPrivateKey,
            ownEphemeralPublicKeyBytes = ownEphemeralPublicKey,
            peerEphemeralPublicKeyBytes = current.peerPublicKey,
            isCaller = false
        )

        _callState.value = CallState.Active(
            current.peerNodeId,
            current.callId,
            current.startTime,
            Timestamp(System.currentTimeMillis()),
            callCrypto
        )

        messagingService.sendCallSignal(current.peerNodeId, signal)
        startVoiceSession(current.callId, current.peerNodeId, callCrypto)

        MeshLogger.info("VoiceCallManager", "Accepted call from ${current.peerNodeId}")
    }

    fun reject() {
        val current = _callState.value
        if (current !is CallState.Ringing) return
        
        val signal = CallSignal(current.callId, CallSignalType.REJECT, ByteArray(0))
        messagingService.sendCallSignal(current.peerNodeId, signal)
        
        _callState.value = CallState.Idle
        MeshLogger.info("VoiceCallManager", "Rejected call from ${current.peerNodeId}")
    }

    fun cancel() {
        val current = _callState.value
        if (current !is CallState.Dialing) return
        
        val signal = CallSignal(current.callId, CallSignalType.CANCEL, ByteArray(0))
        messagingService.sendCallSignal(current.peerNodeId, signal)
        
        _callState.value = CallState.Idle
        MeshLogger.info("VoiceCallManager", "Cancelled outgoing call to ${current.peerNodeId}")
    }

    fun hangup() {
        val current = _callState.value
        if (current is CallState.Idle) return

        // Capture peer/call details before mutating state.
        val peerNodeId = when (current) {
            is CallState.Dialing -> current.peerNodeId
            is CallState.Ringing -> current.peerNodeId
            is CallState.Active -> current.peerNodeId
            is CallState.Ended -> current.peerNodeId
            CallState.Idle -> null
        }

        // Notify the peer BEFORE tearing down local state so the signal has the
        // best chance to be sent while routes and keys are still known.
        val callId = when (current) {
            is CallState.Dialing -> current.callId
            is CallState.Ringing -> current.callId
            is CallState.Active -> current.callId
            is CallState.Ended -> null
            CallState.Idle -> null
        }
        if (peerNodeId != null && callId != null) {
            val signal = CallSignal(callId, CallSignalType.HANGUP, ByteArray(0))
            messagingService.sendCallSignal(peerNodeId, signal)
            // Retry once after a short delay in case the first attempt is queued
            // behind a discovery request or dropped due to a transient route issue.
            scope.launch {
                delay(500.milliseconds)
                messagingService.sendCallSignal(peerNodeId, signal)
            }
            MeshLogger.info("VoiceCallManager", "Sent HANGUP to $peerNodeId")
        }

        // Now stop local audio and clear state.
        stopVoiceSession()
        _callState.value = CallState.Idle

        MeshLogger.info("VoiceCallManager", "Call hung up")
    }

    private fun endCallWithReason(reason: String) {
        val current = _callState.value
        val peerNodeId = when (current) {
            is CallState.Dialing -> current.peerNodeId
            is CallState.Ringing -> current.peerNodeId
            is CallState.Active -> current.peerNodeId
            else -> return
        }

        stopVoiceSession()
        _callState.value = CallState.Ended(peerNodeId, reason)

        scope.launch {
            delay(config.callEndedDisplayMs.milliseconds)
            if (_callState.value is CallState.Ended) {
                _callState.value = CallState.Idle
            }
        }
    }

    private fun startVoiceSession(callId: MessageId, peerNodeId: NodeId, callCrypto: CallCrypto) {
        stopVoiceSession()
        val session = VoiceSessionManager(context, meshService, callId, peerNodeId, callCrypto)
        voiceSession = session
        session.start()
    }

    private fun stopVoiceSession() {
        voiceSession?.stop()
        voiceSession = null
    }

    private fun handleIncomingSignal(sourceNodeId: NodeId, signal: CallSignal) {
        when (signal.type) {
            CallSignalType.OFFER -> {
                if (_callState.value is CallState.Idle) {
                    val offer = CallSignalProtocol.callOffer.read(signal.payload, 0).value
                    _callState.value = CallState.Ringing(
                        sourceNodeId, 
                        signal.callId, 
                        Timestamp(System.currentTimeMillis()),
                        offer.ephemeralPublicKey
                    )
                    MeshLogger.info("VoiceCallManager", "Incoming call from $sourceNodeId")
                    
                    // Start ringing timeout
                    scope.launch {
                        delay(config.callRingingTimeoutMs.milliseconds)
                        val current = _callState.value
                        if (current is CallState.Ringing && current.callId == signal.callId) {
                            MeshLogger.info("VoiceCallManager", "Ringing timeout for $sourceNodeId")
                            reject()
                            endCallWithReason("Missed call")
                        }
                    }
                } else {
                    val busy = CallSignal(signal.callId, CallSignalType.BUSY, ByteArray(0))
                    messagingService.sendCallSignal(sourceNodeId, busy)
                }
            }
            CallSignalType.ACCEPT -> {
                val current = _callState.value
                if (current is CallState.Dialing && current.peerNodeId == sourceNodeId) {
                    val accept = try {
                        CallSignalProtocol.callAccept.read(signal.payload, 0).value
                    } catch (e: Exception) {
                        MeshLogger.error("VoiceCallManager", "Failed to parse ACCEPT from $sourceNodeId", e.toString())
                        return
                    }
                    val callCrypto = CallCrypto(
                        ownEphemeralPrivateKeyBytes = ownEphemeralPrivateKey,
                        ownEphemeralPublicKeyBytes = ownEphemeralPublicKey,
                        peerEphemeralPublicKeyBytes = accept.ephemeralPublicKey,
                        isCaller = true
                    )
                    _callState.value = CallState.Active(
                        sourceNodeId,
                        current.callId,
                        current.startTime,
                        Timestamp(System.currentTimeMillis()),
                        callCrypto
                    )
                    startVoiceSession(current.callId, sourceNodeId, callCrypto)
                    MeshLogger.info("VoiceCallManager", "Call accepted by $sourceNodeId")
                }
            }
            CallSignalType.CANCEL -> {
                val current = _callState.value
                if (current is CallState.Ringing && current.peerNodeId == sourceNodeId) {
                    endCallWithReason("Caller cancelled")
                }
            }
            CallSignalType.HANGUP, CallSignalType.REJECT, CallSignalType.BUSY -> {
                val current = _callState.value
                val isRelevant = when (current) {
                    is CallState.Dialing -> current.peerNodeId == sourceNodeId
                    is CallState.Ringing -> current.peerNodeId == sourceNodeId
                    is CallState.Active -> current.peerNodeId == sourceNodeId
                    else -> false
                }

                if (isRelevant) {
                    // Stop audio immediately on remote hangup/reject/busy.
                    stopVoiceSession()
                    val reason = when (signal.type) {
                        CallSignalType.REJECT -> "Declined"
                        CallSignalType.BUSY -> "Busy"
                        else -> "Remote hung up"
                    }
                    endCallWithReason(reason)
                }
            }
        }
    }
}
