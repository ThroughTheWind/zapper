package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.CredentialStore
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
