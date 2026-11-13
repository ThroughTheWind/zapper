package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.CredentialStore
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import io.github.throughthewind.zapper.core.remote.TvAdapter
import io.github.throughthewind.zapper.core.remote.TvSession
import okhttp3.OkHttpClient

/** Samsung Tizen smart TVs (2016 and later) over the local WebSocket remote API. */
class SamsungAdapter(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val appName: String = "Zapper",
) : TvAdapter {
    override val id: String = ID
    override val displayName: String = "Samsung"

    override fun recognise(candidate: DiscoveredTv): TvDevice? {
        val isSamsung = candidate.manufacturer?.contains("samsung", ignoreCase = true) == true
        if (!isSamsung) return null
        return TvDevice(
            id = candidate.attributes["udn"] ?: candidate.host,
            name = candidate.name ?: "Samsung TV",
            host = candidate.host,
            adapterId = ID,
            model = candidate.model,
        )
    }

    override fun createSession(
        device: TvDevice,
        credentials: CredentialStore,
    ): TvSession =
        SamsungSession(
            device = device,
            credentials = credentials,
            client = TvTls.trustingTvCertificate(httpClient, device.host),
            appName = appName,
        )

    companion object {
        const val ID = "samsung"
    }
}
