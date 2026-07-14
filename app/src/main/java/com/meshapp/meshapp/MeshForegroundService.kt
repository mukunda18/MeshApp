package com.meshapp.meshapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.meshapp.logger.MeshLogger
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the mesh + messaging lifecycle.
 *
 * The service exists only while the mesh is ON. Turning the mesh on starts the
 * service (and its persistent notification); turning it off stops both.
 */
class MeshForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        MeshLogger.info("ForegroundService", "Mesh Foreground Service Created")
        // Show the notification immediately — required within a few seconds of
        // startForegroundService(), and needs no DI container.
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMeshAndSelf()
                return START_NOT_STICKY
            }
            // ACTION_START or a system restart (null intent) → start the mesh.
            else -> startMesh()
        }
        return START_STICKY
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh Network",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the mesh network running in the background"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeshApp")
            .setContentText("Mesh network is running")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "mesh_foreground_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.minor.meshapp.action.START_MESH"
        const val ACTION_STOP = "com.minor.meshapp.action.STOP_MESH"

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
    }
}
