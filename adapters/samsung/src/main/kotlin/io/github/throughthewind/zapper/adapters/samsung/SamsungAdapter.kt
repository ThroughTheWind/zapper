package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.CredentialStore
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import io.github.throughthewind.zapper.core.remote.PowerState
import io.github.throughthewind.zapper.core.remote.TvAdapter
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Samsung Tizen smart TVs (2016 and later) over the local WebSocket remote API. */
class SamsungAdapter internal constructor(
    private val httpClient: OkHttpClient,
    private val appName: String,
    private val ssdpSearch: (List<String>) -> Flow<Ssdp.Response>,
    private val infoPort: Int,
    private val subnetHosts: () -> Flow<String> = { SubnetScan(infoPort).hosts() },
    private val wake: suspend (mac: String, host: String) -> Unit = WakeOnLan::send,
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

    /**
     * SSDP for the search targets a Samsung TV answers, plus a sweep of the phone's subnet for the
     * TV's info port, for networks that drop multicast. Every candidate is confirmed by the TV's
     * info endpoint (SPEC FR-1.2), once per host.
     */
    override fun discover(): Flow<DiscoveredTv> {
        val asked = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        return merge(ssdpSearch(SEARCH_TARGETS).map { it.host }, subnetHosts())
            .filter { synchronized(asked) { asked.add(it) } }
            .mapNotNull { probe(it) }
            .filter { synchronized(seen) { seen.add(it.identity) } }
    }

    override suspend fun probe(host: String): DiscoveredTv? =
        withContext(Dispatchers.IO) {
            runCatching {
                probeClient.newCall(Request.Builder().url(SamsungDeviceInfo.url(host, infoPort)).build()).execute().use { response ->
                    if (!response.isSuccessful) null else SamsungDeviceInfo.parse(host, response.body.string())
                }
            }.getOrNull()
        }

    override fun canPowerOn(device: TvDevice): Boolean = device.macAddress != null

    /** Wake-on-LAN to the MAC the TV gave at discovery (research §2.5). */
    override suspend fun powerOn(device: TvDevice) {
        val mac = device.macAddress ?: throw TvException(TvError.Unsupported("turning on a TV without its MAC address"))
        wake(mac, device.host)
    }

    /**
     * `PowerState` from the info endpoint on 2019+ TVs. An older TV that answers without one is
     * on; one that does not answer is unknown (research §2.5).
     */
    override suspend fun powerState(device: TvDevice): PowerState? {
        val info = probe(device.host) ?: return null
        return when (info.properties[SamsungDeviceInfo.PROPERTY_POWER_STATE]?.lowercase()) {
            "standby" -> PowerState.STANDBY
            else -> PowerState.ON
        }
    }

    override val powerOnHelp: String =
        "On the TV, open Settings › General › Network › Expert Settings and turn on Power On with Mobile. " +
            "Some TVs only wake when connected to the router by cable."

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

        /** Samsung's own receiver first; some firmware only answers DIAL or the UPnP renderer (research §2.1). */
        internal val SEARCH_TARGETS =
            listOf(
                "urn:samsung.com:device:RemoteControlReceiver:1",
                "urn:dial-multiscreen-org:service:dial:1",
                "urn:schemas-upnp-org:device:MediaRenderer:1",
            )
        private const val PROBE_TIMEOUT_MS = 2_000L
    }
}
