package com.minor.ui.viewmodel

/**
 * Abstraction over the platform mesh lifecycle (the foreground service).
 *
 * The ui module must not depend on the app module, so the app provides a concrete
 * implementation (starting/stopping MeshForegroundService) and injects it here.
 */
interface MeshController {
    /** Turn the mesh ON: start the foreground service and its notification. */
    fun start()

    /** Turn the mesh OFF: stop the foreground service and remove its notification. */
    fun stop()
}
