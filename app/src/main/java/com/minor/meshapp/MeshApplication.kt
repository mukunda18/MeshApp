package com.minor.meshapp

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Application subclass — root of manual dependency injection.
 *
 * Registered in AndroidManifest.xml via android:name=".MeshApplication".
 *
 * Responsibilities:
 *   1. Create AppContainer (builds MeshConfig, MeshService, MessagingService).
 *   2. Start mesh and messaging services on application launch.
 *   3. Provide a stable applicationScope for suspend lifecycle calls.
 *
 * Usage from any Activity / Fragment / ViewModel factory:
 *   val app = context.applicationContext as MeshApplication
 *   val meshService   = app.container.meshService
 *   val messagingService = app.container.messagingService
 */
class MeshApplication : Application() {

    /** Application-scoped coroutine scope. Cancelled in onTerminate (emulator / test only). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Singleton DI container. Available after onCreate() returns. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        container = AppContainer(this)

        // Start the mesh networking layer.
        applicationScope.launch {
            container.meshService.start()
        }

        // Start the messaging layer (seeds conversationsStream from SQLite,
        // launches incoming-message and delivery-status collectors).
        container.messagingService.start()
    }

    override fun onTerminate() {
        // onTerminate() is only called reliably in emulators and unit-test runners.
        // On a real device the OS kills the process; sockets are reclaimed by the kernel.
        applicationScope.launch {
            container.meshService.stop()
        }
        container.messagingService.stop()
        applicationScope.cancel()
        super.onTerminate()
    }
}
