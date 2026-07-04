package com.minor.routing

import com.minor.model.NodeId
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread safe routing table keyed on NodeId hex strings
 * Accepts a new entry only when it is strictly better than the existing hop count
 */
class Router {

    private val table = ConcurrentHashMap<String, RouteInfo>()

    /** Returns the next hop NodeId for a destination or null when no valid route exists */
    fun lookup(destinationNodeId: NodeId): NodeId? {
        val entry = table[destinationNodeId.toString()] ?: return null
        return if (entry.valid) entry.nextHopNodeId else null
    }

    /** Installs a route only when it is new or has a newer timestamp (or better hop count if same timestamp) */
    fun update(
        destinationNodeId: NodeId,
        nextHopNodeId: NodeId,
        hopCount: Int,
        routeTimestamp: Long = System.currentTimeMillis()
    ) {
        val key = destinationNodeId.toString()
        val existing = table[key]
        
        val isBetter = existing == null || !existing.valid || 
            (routeTimestamp > existing.routeTimestamp) ||
            (routeTimestamp == existing.routeTimestamp && hopCount < existing.hopCount)

        if (isBetter) {
            table[key] = RouteInfo(destinationNodeId, nextHopNodeId, hopCount, routeTimestamp)
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
        for ((route, info) in table) {
            if (info.valid && route == targetKey) {
                table[route] = info.copy(valid = false)
                affected.add(info.destinationNodeId)
            }
        }
        return affected
    }

    /** Returns a snapshot of all entries currently marked as valid */
    fun getRoutes(): List<RouteInfo> = table.values.filter { it.valid }.toList()
}