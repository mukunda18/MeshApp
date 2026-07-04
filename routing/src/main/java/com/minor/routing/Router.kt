package com.minor.routing

import com.minor.model.NodeId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Thread safe routing table keyed on NodeId hex strings
 * Accepts a new entry only when it is strictly better than the existing hop count
 */
class Router(
    private val routeExpiryMs: Long = 60_000,
    private val expiryCheckIntervalMs: Long = 10_000
) {

    private val table = ConcurrentHashMap<String, RouteInfo>()

    /** Returns the next hop NodeId for a destination or null when no valid route exists */
    fun lookup(destinationNodeId: NodeId): NodeId? {
        val entry = table[destinationNodeId.toString()] ?: return null
        return if (entry.valid) entry.nextHopNodeId else null
    }

    /** Installs a route only when it is new or strictly fewer hops than the stored one */
    fun update(destinationNodeId: NodeId, nextHopNodeId: NodeId, hopCount: Int) {
        val key = destinationNodeId.toString()
        val now = System.currentTimeMillis()
        val existing = table[key]
        if (existing == null || !existing.valid || hopCount < existing.hopCount) {
            table[key] = RouteInfo(destinationNodeId, nextHopNodeId, hopCount, now)
        }
    }

    /** Marks one route as invalid without removing it so a fresh RREQ may replace it */
    fun invalidate(destinationNodeId: NodeId) {
        val key = destinationNodeId.toString()
        table[key]?.let { table[key] = it.copy(valid = false) }
    }

    /** Marks every route whose next hop matches the given node as invalid */
    fun invalidateVia(nextHopNodeId: NodeId): List<NodeId> {
        val targetKey = nextHopNodeId.toString()
        val affected = mutableListOf<NodeId>()
        for ((_, info) in table) {
            if (info.valid && info.nextHopNodeId.toString() == targetKey) {
                table[info.destinationNodeId.toString()] = info.copy(valid = false)
                affected.add(info.destinationNodeId)
            }
        }
        return affected
    }

    /** Returns a snapshot of all entries currently marked as valid */
    fun getRoutes(): List<RouteInfo> = table.values.filter { it.valid }.toList()

    /** Starts the background coroutine that removes entries older than routeExpiryMs */
    fun startExpiryLoop(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                delay(expiryCheckIntervalMs)
                val now = System.currentTimeMillis()
                table.values
                    .filter { now - it.lastUpdated > routeExpiryMs }
                    .forEach { table.remove(it.destinationNodeId.toString()) }
            }
        }
    }
}