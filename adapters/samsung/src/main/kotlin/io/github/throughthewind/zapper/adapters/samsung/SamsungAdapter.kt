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
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Samsung smart TVs: Tizen (2016 and later) over the local WebSocket remote API, and 2008–2013
 * models over the legacy TCP protocol on port 55000 ([LegacyProtocol]).
 */
class SamsungAdapter internal constructor(
    private val httpClient: OkHttpClient,
    private val appName: String,
    private val ssdpSearch: (List<String>) -> Flow<Ssdp.Response>,
    private val infoPort: Int,
    private val subnetHosts: () -> Flow<String> = { SubnetScan(infoPort).hosts() },
    private val wake: suspend (mac: String, host: String) -> Unit = WakeOnLan::send,
    private val legacyPort: Int = LegacyProtocol.PORT,
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
     * info endpoint (SPEC FR-1.2), once per host. A host whose SSDP answer names Samsung but has
     * no info endpoint is a 2008–2013 TV if it accepts the legacy port.
     */
    override fun discover(): Flow<DiscoveredTv> {
        val asked = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        return merge(ssdpSearch(SEARCH_TARGETS).map { it.host to it }, subnetHosts().map { it to null })
            .filter { (host, _) -> synchronized(asked) { asked.add(host) } }
            .mapNotNull { (host, ssdp) ->
                tizenProbe(host) ?: ssdp?.takeIf { it.isSamsung() }?.let { legacyProbe(host, it.uuid()) }
            }.filter { synchronized(seen) { seen.add(it.identity) } }
    }

    /** The info endpoint first; failing that, a TV that accepts the legacy port is taken as a 2008–2013 Samsung. */
    override suspend fun probe(host: String): DiscoveredTv? = tizenProbe(host) ?: legacyProbe(host, uniqueId = null)

    private suspend fun tizenProbe(host: String): DiscoveredTv? =
        withContext(Dispatchers.IO) {
            runCatching {
                probeClient.newCall(Request.Builder().url(SamsungDeviceInfo.url(host, infoPort)).build()).execute().use { response ->
                    if (!response.isSuccessful) null else SamsungDeviceInfo.parse(host, response.body.string())
                }
            }.getOrNull()
        }

    /**
     * A 2008–2013 TV has no info endpoint, so an open legacy port is all there is to go on: no
     * model, no MAC, and so no power-on.
     */
    private suspend fun legacyProbe(
        host: String,
        uniqueId: String?,
    ): DiscoveredTv? {
        if (!acceptsLegacyPort(host)) return null
        return DiscoveredTv(
            adapterId = ID,
            host = host,
            name = LEGACY_NAME,
            uniqueId = uniqueId,
            properties = mapOf(PROPERTY_PROTOCOL to PROTOCOL_LEGACY),
        )
    }

    private suspend fun acceptsLegacyPort(host: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { Socket().use { it.connect(InetSocketAddress(host, legacyPort), PROBE_TIMEOUT_MS.toInt()) } }.isSuccess
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
        // A 2008–2013 TV drops off the network in standby: answering means on.
        if (device.isLegacy()) return if (acceptsLegacyPort(device.host)) PowerState.ON else null
        val info = tizenProbe(device.host) ?: return null
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
        if (device.isLegacy()) return LegacySamsungSession(device, credentials, appName, legacyPort)
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

        /** Which remote protocol a saved TV speaks; absent on Tizen TVs, including every TV saved before 2008–2013 support. */
        internal const val PROPERTY_PROTOCOL = "protocol"
        internal const val PROTOCOL_LEGACY = "legacy"
        internal const val LEGACY_NAME = "Samsung TV"

        private fun TvDevice.isLegacy() = properties[PROPERTY_PROTOCOL] == PROTOCOL_LEGACY

        /** Samsung's UPnP stack names itself in the SERVER header, for example `SHP, UPnP/1.0, Samsung UPnP SDK/1.0`. */
        internal fun Ssdp.Response.isSamsung(): Boolean =
            listOf("SERVER", "ST", "USN").any { headers[it]?.contains("samsung", ignoreCase = true) == true }

        /** The device's UUID from `USN: uuid:…::urn:…`, so the TV is merged when it answers several search targets. */
        internal fun Ssdp.Response.uuid(): String? = headers["USN"]?.substringBefore("::")?.takeIf { it.startsWith("uuid:") }
    }
}
