package com.meshapp.routing

import com.meshapp.model.NodeId
import com.meshapp.logger.MeshLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Thread safe routing table keyed on NodeId hex strings
 * Accepts a new entry only when it is strictly better than the existing hop count
 */
class Router {

    private val table = ConcurrentHashMap<NodeId, RouteInfo>()

    /** Returns the next hop NodeId for a destination or null when no valid route exists */
    fun lookup(destinationNodeId: NodeId): NodeId? {
        val entry = table[destinationNodeId] ?: return null
        return if (entry.valid) entry.nextHopNodeId else null
    }

    /** Installs a route only when it is new or has a newer timestamp (or better hop count if same timestamp) */
    fun update(
        destinationNodeId: NodeId,
        nextHopNodeId: NodeId,
        hopCount: Int,
        routeTimestamp: Long = System.currentTimeMillis()
    ) {
        val existing = table[destinationNodeId]
        
        val isBetter = existing == null || !existing.valid || 
            (routeTimestamp > existing.routeTimestamp) ||
            (routeTimestamp == existing.routeTimestamp && hopCount < existing.hopCount)

        if (isBetter) {
            val old = table[destinationNodeId]
            table[destinationNodeId] = RouteInfo(destinationNodeId, nextHopNodeId, hopCount, routeTimestamp)
            if (old == null) {
                MeshLogger.info("Router", "New route to $destinationNodeId via $nextHopNodeId", "Hops: $hopCount")
            } else if (old.nextHopNodeId != nextHopNodeId || old.hopCount != hopCount) {
                MeshLogger.info("Router", "Updated route to $destinationNodeId", "Via $nextHopNodeId, Hops: $hopCount (was ${old.hopCount})")
            }
        }
    }

    /** Marks one route as invalid without removing it so a fresh RREQ may replace it */
    fun invalidate(destinationNodeId: NodeId) {
        table[destinationNodeId]?.let { 
            if (it.valid) {
                MeshLogger.info("Router", "Invalidated route to $destinationNodeId")
                table[destinationNodeId] = it.copy(valid = false)
            }
        }
    }

    /** Marks every route whose next hop matches the given node as invalid */
    fun invalidateVia(nextHopNodeId: NodeId): List<NodeId> {
        val affected = mutableListOf<NodeId>()
        for ((nodeId, info) in table) {
            if (info.valid && info.nextHopNodeId == nextHopNodeId) {
                table[nodeId] = info.copy(valid = false)
                affected.add(info.destinationNodeId)
            }
        }
        return affected
    }

    /** Returns a snapshot of all entries currently marked as valid */
    fun getRoutes(): List<RouteInfo> = table.values.filter { it.valid }.toList()

    /** Removes routes that have been invalid for longer than the expiry window */
    fun prune(expiryMs: Long) {
        val now = System.currentTimeMillis()
        table.values.removeIf { !it.valid && (now - it.routeTimestamp > expiryMs) }
    }

    /** Starts a background maintenance loop for route pruning */
    fun startPruning(scope: CoroutineScope, freshnessWindowMs: Long) {
        scope.launch {
            val pruneInterval = freshnessWindowMs / 2
            while (isActive) {
                delay(pruneInterval.milliseconds)
                prune(freshnessWindowMs * 10)
            }
        }
    }
}