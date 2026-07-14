package com.meshapp.meshapp

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
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
 *   1. Build AppContainer OFF the main thread (SQLite open, EC keypair generation,
 *      KeyStore access) so it never blocks the first frame / notification.
 *   2. Provide a stable applicationScope and awaitContainer() for consumers.
 *
 * The mesh starts OFF. The user turns it on/off from the UI, which starts/stops
 * MeshForegroundService (Option 1: notification reflects real mesh state).
 *
 * Usage from an Activity / ViewModel factory (container may not be ready yet):
 *   val app = context.applicationContext as MeshApplication
 *   val container = app.awaitContainer()   // suspend until built
 */
class MeshApplication : Application() {

    /** Application-scoped coroutine scope. Cancelled in onTerminate (emulator / test only). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val containerDeferred = CompletableDeferred<AppContainer>()

    /** Singleton DI container. Set once the background build completes. */
    lateinit var container: AppContainer
        private set

    /** True once the container has finished building. */
    val isContainerReady: Boolean get() = containerDeferred.isCompleted

    /** Suspends until the DI container has been built, then returns it. */
    suspend fun awaitContainer(): AppContainer = containerDeferred.await()

    override fun onCreate() {
        super.onCreate()

        // Build the DI container off the main thread. SQLite open, EC keypair
        // generation and KeyStore access must not block the first frame.
        // The mesh itself is NOT started here — it starts OFF and is turned on
        // from the UI, which launches MeshForegroundService.
        applicationScope.launch(Dispatchers.Default) {
            try {
                val built = AppContainer(this@MeshApplication)
                container = built
                containerDeferred.complete(built)
            } catch (e: Exception) {
                // If AppContainer fails, the app cannot function.
                // Possible exceptions:
                // - SQLiteException (failed to open nodes/conversations DB)
                // - KeyStoreException (failed to access Android Keystore for identity)
                // - SecurityException (failed to generate EC keys)
                containerDeferred.completeExceptionally(e)
            }
        }
    }

    override fun onTerminate() {
        // onTerminate() is only called reliably in emulators and unit-test runners.
        // On a real device the OS kills the process; sockets are reclaimed by the kernel.
        stopService(Intent(this, MeshForegroundService::class.java))
        applicationScope.cancel()
        super.onTerminate()
    }
}
