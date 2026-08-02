package com.meshapp.meshapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.meshapp.logger.MeshLogger
import com.meshapp.voice.VoiceSimulator
import com.meshapp.voice.CallState
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the mesh + messaging lifecycle.
 *
 * The service exists only while the mesh is ON. Turning the mesh on starts the
 * service (and its persistent notification); turning it off stops both.
 */
class MeshForegroundService : Service() {

    private lateinit var voiceSimulator: VoiceSimulator
    private var fullScreenPresentedCallId: String? = null

    override fun onCreate() {
        super.onCreate()
        voiceSimulator = VoiceSimulator(this)
        createNotificationChannels()
        MeshLogger.info("ForegroundService", "Mesh Foreground Service Created")
        
        // Show the notification immediately
        startForeground(
            NOTIFICATION_ID, 
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        observeCallState()
    }

    private fun observeCallState() {
        val app = application as MeshApplication
        app.applicationScope.launch {
            val container = app.awaitContainer()
            container.voiceCallManager.callState.collect { state ->
                if (state is CallState.Idle) {
                    fullScreenPresentedCallId = null
                }
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as MeshApplication
        when (intent?.action) {
            ACTION_STOP -> {
                stopMeshAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START_VOICE_SIM -> {
                voiceSimulator.start()
                _isVoiceSimActive.value = true
                updateNotification()
            }
            ACTION_STOP_VOICE_SIM -> {
                voiceSimulator.stop()
                _isVoiceSimActive.value = false
                updateNotification()
            }
            ACTION_CALL_ACCEPT -> {
                app.applicationScope.launch {
                    app.awaitContainer().voiceCallManager.accept()
                }
            }
            ACTION_CALL_REJECT -> {
                app.applicationScope.launch {
                    app.awaitContainer().voiceCallManager.reject()
                }
            }
            ACTION_CALL_HANGUP -> {
                app.applicationScope.launch {
                    app.awaitContainer().voiceCallManager.hangup()
                }
            }
            ACTION_CALL_CANCEL -> {
                app.applicationScope.launch {
                    app.awaitContainer().voiceCallManager.cancel()
                }
            }
            // ACTION_START or a system restart (null intent) → start the mesh.
            else -> startMesh()
        }
        return START_STICKY
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startMesh() {
        val app = application as MeshApplication
        // Container is built off the main thread; await it before starting mesh.
        app.applicationScope.launch(Dispatchers.Default) {
            try {
                val container = app.awaitContainer()
                container.meshService.start()
                container.messagingService.start()
                MeshLogger.info("ForegroundService", "Mesh and Messaging services started")
            } catch (e: Exception) {
                // Critical failure during background mesh startup.
                // - IllegalStateException (MeshService already started)
                // - SQLiteException (failed to open DBs)
                // - SocketException (failed to bind ports)
                android.util.Log.e("MeshForegroundService", "Failed to start mesh services", e)
                MeshLogger.error("ForegroundService", "Failed to start mesh services", e.toString())
                stopMeshAndSelf()
            }
        }
    }

    private fun stopMeshAndSelf() {
        MeshLogger.info("ForegroundService", "Stopping Mesh Foreground Service...")
        val app = application as MeshApplication
        if (app.isContainerReady) {
            app.applicationScope.launch(Dispatchers.Default) {
                val container = app.awaitContainer()
                container.meshService.stop()
                container.messagingService.stop()
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        val app = application as MeshApplication
        if (app.isContainerReady) {
            app.applicationScope.launch(Dispatchers.Default) {
                val container = app.awaitContainer()
                container.meshService.stop()
                container.messagingService.stop()
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        val meshChannel = NotificationChannel(
            MESH_CHANNEL_ID,
            "Mesh Network",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the mesh network running in the background"
        }

        val callChannel = NotificationChannel(
            CALL_CHANNEL_ID,
            "Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming and active call controls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(meshChannel, callChannel))
    }

    private fun buildNotification(): Notification {
        val app = application as MeshApplication
        val container = if (app.isContainerReady) app.container else null
        val callState = container?.voiceCallManager?.callState?.value ?: CallState.Idle
        val channelId = if (callState is CallState.Idle) MESH_CHANNEL_ID else CALL_CHANNEL_ID

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        when (callState) {
            is CallState.Dialing -> {
                builder.setContentTitle("Calling...")
                builder.setContentText("Calling ${callState.peerNodeId}")
                builder.addAction(
                    0, "Cancel",
                    createActionIntent(ACTION_CALL_CANCEL)
                )
                builder.setPriority(NotificationCompat.PRIORITY_MAX)
                builder.setCategory(NotificationCompat.CATEGORY_CALL)
            }
            is CallState.Ringing -> {
                builder.setContentTitle("Incoming Call")
                builder.setContentText("From ${callState.peerNodeId}")
                builder.addAction(
                    0, "Accept",
                    createActionIntent(ACTION_CALL_ACCEPT)
                )
                builder.addAction(
                    0, "Reject",
                    createActionIntent(ACTION_CALL_REJECT)
                )
                builder.setPriority(NotificationCompat.PRIORITY_MAX)
                builder.setCategory(NotificationCompat.CATEGORY_CALL)
                if (shouldLaunchFullScreen(callState)) {
                    builder.setFullScreenIntent(openAppIntent, true)
                }
            }
            is CallState.Active -> {
                builder.setContentTitle("In Call")
                builder.setContentText("Call active with ${callState.peerNodeId}")
                builder.addAction(
                    0, "Hangup",
                    createActionIntent(ACTION_CALL_HANGUP)
                )
                builder.setCategory(NotificationCompat.CATEGORY_CALL)
                builder.setPriority(NotificationCompat.PRIORITY_LOW)
            }
            is CallState.Ended -> {
                builder.setContentTitle("Call Ended")
                builder.setContentText(callState.reason)
                builder.setPriority(NotificationCompat.PRIORITY_LOW)
            }
            CallState.Idle -> {
                val contentText = if (_isVoiceSimActive.value) {
                    "Mesh network is running (Voice Simulation Active)"
                } else {
                    "Mesh network is running"
                }
                builder.setContentTitle("MeshApp")
                builder.setContentText(contentText)
                builder.setPriority(NotificationCompat.PRIORITY_LOW)
            }
        }

        return builder.build()
    }

    private fun shouldLaunchFullScreen(callState: CallState): Boolean {
        val callId = when (callState) {
            is CallState.Ringing -> callState.callId.toString()
            else -> return false
        }
        if (fullScreenPresentedCallId == callId) return false
        fullScreenPresentedCallId = callId
        return true
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, MeshForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val MESH_CHANNEL_ID = "mesh_foreground_channel"
        private const val CALL_CHANNEL_ID = "mesh_call_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.minor.meshapp.action.START_MESH"
        const val ACTION_STOP = "com.minor.meshapp.action.STOP_MESH"
        const val ACTION_START_VOICE_SIM = "com.minor.meshapp.action.START_VOICE_SIM"
        const val ACTION_STOP_VOICE_SIM = "com.minor.meshapp.action.STOP_VOICE_SIM"
        const val ACTION_CALL_ACCEPT = "com.minor.meshapp.action.CALL_ACCEPT"
        const val ACTION_CALL_REJECT = "com.minor.meshapp.action.CALL_REJECT"
        const val ACTION_CALL_CANCEL = "com.minor.meshapp.action.CALL_CANCEL"
        const val ACTION_CALL_HANGUP = "com.minor.meshapp.action.CALL_HANGUP"

        private val _isVoiceSimActive = MutableStateFlow(false)
        val isVoiceSimActive = _isVoiceSimActive.asStateFlow()

        /** Starts the mesh: launches the foreground service and shows the notification. */
        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        /** Stops the mesh: tears down the service and removes the notification. */
        fun stop(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun startVoiceSim(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_START_VOICE_SIM
            }
            context.startService(intent)
        }

        fun stopVoiceSim(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_STOP_VOICE_SIM
            }
            context.startService(intent)
        }
    }
}
