package io.github.throughthewind.zapper

import android.app.Application
import io.github.throughthewind.zapper.adapters.samsung.SamsungAdapter
import io.github.throughthewind.zapper.connection.MulticastLock
import io.github.throughthewind.zapper.connection.NetworkMonitor
import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.remote.AdapterRegistry
import io.github.throughthewind.zapper.core.remote.TvConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZapperApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.networkMonitor.start()
    }
}

/**
 * Every long-lived object, built by hand (ADR-0001). Adding a brand is one line in [adapters].
 * This file and `BuildAdapters.kt` are the only app code allowed to name an adapter
 * (`./gradlew checkAdapterBoundaries`).
 */
class AppContainer(
    application: Application,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val repository = ZapperRepository(application)

    val adapters =
        AdapterRegistry(
            listOf(
                SamsungAdapter(),
            ) + buildTypeAdapters(),
        )

    val multicastLock = MulticastLock(application)

    val connection = TvConnection(adapters, repository, scope, onConnected = repository::saveDevice)

    // The callback arrives on a binder thread; the connection lives on the main thread.
    val networkMonitor = NetworkMonitor(application) { scope.launch { connection.networkAvailable() } }
}
