package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.CredentialStore
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import io.github.throughthewind.zapper.core.remote.TvAdapter
import io.github.throughthewind.zapper.core.remote.TvSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Samsung Tizen smart TVs (2016 and later) over the local WebSocket remote API. */
class SamsungAdapter internal constructor(
    private val httpClient: OkHttpClient,
    private val appName: String,
    private val ssdpSearch: (String) -> Flow<Ssdp.Response>,
    private val infoPort: Int,
) : TvAdapter {
    constructor(
        httpClient: OkHttpClient = OkHttpClient(),
        appName: String = "Zapper",
    ) : this(httpClient, appName, Ssdp()::search, SamsungDeviceInfo.INFO_PORT)

    override val id: String = ID
    override val displayName: String = "Samsung"

    private val probeClient =
        httpClient
            .newBuilder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
            .build()

    /** SSDP for Samsung's remote control receiver, confirmed by the TV's info endpoint (SPEC FR-1.2). */
    override fun discover(): Flow<DiscoveredTv> {
        val asked = mutableSetOf<String>()
        return ssdpSearch(SEARCH_TARGET)
            .filter { asked.add(it.host) }
            .mapNotNull { probe(it.host) }
            .distinctUntilChangedBy { it.identity }
    }

    override suspend fun probe(host: String): DiscoveredTv? =
        withContext(Dispatchers.IO) {
            runCatching {
                probeClient.newCall(Request.Builder().url(SamsungDeviceInfo.url(host, infoPort)).build()).execute().use { response ->
                    if (!response.isSuccessful) null else SamsungDeviceInfo.parse(host, response.body.string())
                }
            }.getOrNull()
        }

    override fun createSession(
        device: TvDevice,
        credentials: CredentialStore,
    ): TvSession {
        // 2016–2017 TVs speak plain ws:// on 8001 without a token (research §2.1).
        val legacy = device.properties[SamsungDeviceInfo.PROPERTY_TOKEN_AUTH] == "false"
        return SamsungSession(
            device = device,
            credentials = credentials,
            client = httpClient,
            appName = appName,
            scheme = if (legacy) "ws" else "wss",
            port = if (legacy) SamsungDeviceInfo.INFO_PORT else SamsungProtocol.SECURE_PORT,
        )
    }

    companion object {
        const val ID = "samsung"
        internal const val SEARCH_TARGET = "urn:samsung.com:device:RemoteControlReceiver:1"
        private const val PROBE_TIMEOUT_MS = 2_000L
    }
}
