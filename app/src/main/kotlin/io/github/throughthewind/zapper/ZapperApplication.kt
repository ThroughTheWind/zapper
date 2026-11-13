package io.github.throughthewind.zapper

import android.app.Application
import io.github.throughthewind.zapper.adapters.samsung.SamsungAdapter
import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.remote.AdapterRegistry

class ZapperApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Every long-lived object, built by hand (ADR-0001). Adding a brand is one line in [adapters].
 */
class AppContainer(
    application: Application,
) {
    val repository = ZapperRepository(application)

    val adapters =
        AdapterRegistry(
            listOf(
                SamsungAdapter(),
            ),
        )
}
