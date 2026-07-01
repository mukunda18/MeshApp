package com.minor.routing

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Maintains the routing table mapping a destination node to its next hop
 * Also runs a periodic expiry loop to drop stale entries
 */
class Router(
    private val routeExpiryMs: Long = 60_000,
    private val expiryCheckIntervalMs: Long = 10_000
) {

    private val table = ConcurrentHashMap<String, RouteInfo>()

    /** Returns the next hop for a destination or null if unknown */
    fun lookup(destinationNodeID: String): String? {
        val entry = table[destinationNodeID] ?: return null
        if (!entry.valid) return null
        return entry.nextHopNodeID
    }

    /** Installs a route only if new or strictly better than the existing one */
    fun update(destinationNodeID: String, nextHopNodeID: String, hopCount: Int) {
        val now = System.currentTimeMillis()
        val existing = table[destinationNodeID]
        if (existing == null || !existing.valid || hopCount < existing.hopCount) {
            table[destinationNodeID] = RouteInfo(destinationNodeID, nextHopNodeID, hopCount, now, true)
        }
    }

    /** Marks a single route as invalid without deleting it */
    fun invalidate(destinationNodeID: String) {
        val existing = table[destinationNodeID] ?: return
        table[destinationNodeID] = existing.copy(valid = false)
    }

    /** Marks all routes whose next hop matches the given node as invalid */
    fun invalidateVia(nextHopNodeID: String): List<String> {
        val affected = mutableListOf<String>()
        for ((dest, info) in table) {
            if (info.valid && info.nextHopNodeID == nextHopNodeID) {
                table[dest] = info.copy(valid = false)
                affected.add(dest)
            }
        }
        return affected
    }

    /** Returns a snapshot of all currently valid routes */
    fun getRoutes(): List<RouteInfo> = table.values.filter { it.valid }

    /** Starts the background loop that expires stale routes */
    fun startExpiryLoop(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                delay(expiryCheckIntervalMs)
                val now = System.currentTimeMillis()
                for ((dest, info) in table) {
                    if (now - info.lastUpdated > routeExpiryMs) {
                        table.remove(dest)
                    }
                }
            }
        }
    }
}