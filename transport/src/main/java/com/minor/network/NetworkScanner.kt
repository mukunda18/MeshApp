package com.minor.network

import java.net.InetAddress
import java.net.NetworkInterface

/** One sendable broadcast endpoint: a local source IP bound to its subnet broadcast address. */
data class NetworkInterfaceInfo(
    val interfaceName: String,
    val localAddress: InetAddress,
    val broadcastAddress: InetAddress,
)

object NetworkScanner {

    /** All usable IPv4 broadcast targets across every up, non-loopback interface (AP + STA). */
    fun getNetworkInterfaceInfo(): List<NetworkInterfaceInfo> =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif ->
                nif.interfaceAddresses.asSequence().mapNotNull { ia ->
                    val broadcast = ia.broadcast ?: return@mapNotNull null
                    NetworkInterfaceInfo(nif.name, ia.address, broadcast)
                }
            }
            .toList()
}
