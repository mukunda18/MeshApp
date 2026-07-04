package com.minor.meshapp.network

import android.content.Context
import com.minor.meshcontrol.MeshConfig
import com.minor.meshcontrol.MeshSocketFactory
import com.minor.meshcontrol.MeshSockets
import com.minor.network.TCPReceiver
import com.minor.network.TCPSender
import com.minor.network.UdpSocket
import kotlinx.coroutines.CoroutineScope
import android.util.Log
/**
 * Android-context-bound implementation of MeshSocketFactory.
 *
 * Creates the three network primitives required by MeshService:
 *   - UdpSocket  — broadcast UDP receiver / sender (uses WifiManager multicast lock)
 *   - TCPReceiver — inbound TCP server
 *   - TCPSender  — outbound TCP connection pool
 *
 * The Context is held as applicationContext to avoid leaking an Activity.
 * Port values come from MeshConfig so a single config source drives everything.
 */
class AndroidMeshSocketFactory(context: Context) : MeshSocketFactory {

    private val appContext: Context = context.applicationContext

    override fun create(scope: CoroutineScope, config: MeshConfig): MeshSockets {
        Log.d(
            "MeshDebug",
            "UDP=${config.udpBroadcastPort}, TCP=${config.tcpPort}"
        )
        return MeshSockets(
            tcpReceiver = TCPReceiver(
                port = config.tcpPort,
                scope = scope
            ),
            tcpSender = TCPSender(
                port = config.tcpPort,
                scope = scope
            ),
            udpSocket = UdpSocket(
                context = appContext,
                port = config.udpBroadcastPort,
                scope = scope,
                useMulticastLock = true
            )
        )
    }
}
