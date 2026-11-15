package io.github.throughthewind.zapper.adapters.roku

import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.CredentialStore
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import io.github.throughthewind.zapper.core.remote.PowerState
import io.github.throughthewind.zapper.core.remote.TvAdapter
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.WakeOnLan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Roku players and Roku TVs (TCL, Hisense, Sharp…) over Roku's documented External Control
 * Protocol (research §5). No pairing: anything on the local network may send keys, unless the
 * Roku's "Control by mobile apps" setting forbids it.
 */
class RokuAdapter internal constructor(
    private val httpClient: OkHttpClient,
    private val ssdpSearch: (List<String>) -> Flow<Ssdp.Response>,
    private val ecpPort: Int,
    private val wake: suspend (mac: String, host: String) -> Unit = WakeOnLan::send,
) : TvAdapter {
    constructor(httpClient: OkHttpClient = OkHttpClient()) : this(httpClient, Ssdp()::search, Ecp.PORT)

    override val id: String = ID
    override val displayName: String = "Roku"

    private val probeClient =
        httpClient
            .newBuilder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
            .build()

    private fun ecp(host: String) = Ecp(probeClient, host, ecpPort)

    /** SSDP `roku:ecp`, each answer confirmed by its device-info, once per host and per Roku. */
    override fun discover(): Flow<DiscoveredTv> {
        val asked = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        return ssdpSearch(listOf(SEARCH_TARGET))
            .map { it.host }
            .filter { synchronized(asked) { asked.add(it) } }
            .mapNotNull { probe(it) }
            .filter { synchronized(seen) { seen.add(it.identity) } }
    }

    override suspend fun probe(host: String): DiscoveredTv? = quietly { RokuDeviceInfo.parse(host, ecp(host).query("device-info")) }

    /** A Roku TV wakes on `PowerOn` with Fast TV start, or on Wake-on-LAN. A stick has no power to turn on. */
    override fun canPowerOn(device: TvDevice): Boolean = device.properties[RokuDeviceInfo.PROPERTY_IS_TV] == "true"

    /**
     * `PowerOn` first: with Fast TV start the TV keeps answering ECP in standby. A TV in deep
     * standby does not answer, so Wake-on-LAN follows when its MAC is known (research §5).
     */
    override suspend fun powerOn(device: TvDevice) {
        if (!canPowerOn(device)) throw TvException(TvError.Unsupported("turning on a Roku player"))
        val error =
            try {
                ecp(device.host).command("keypress", POWER_ON)
                return
            } catch (e: TvException) {
                e
            }
        // A Roku that answered with 403 is awake and blocks phones: waking it again changes nothing.
        if (error.error is TvError.ControlDisabled) throw error
        val mac = device.macAddress ?: throw error
        wake(mac, device.host)
    }

    override suspend fun powerState(device: TvDevice): PowerState? =
        quietly {
            RokuDeviceInfo.powerState(ecp(device.host).query("device-info"))
        }

    override val powerOnHelp: String =
        "On the Roku TV, open Settings › System › Power and turn on Fast TV start. " +
            "If it is on, check that Settings › System › Advanced system settings › Control by mobile apps is not Disabled."

    override fun createSession(
        device: TvDevice,
        credentials: CredentialStore,
    ): TvSession = RokuSession(device, Ecp(httpClient, device.host, ecpPort))

    private suspend fun <T> quietly(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    companion object {
        const val ID = "roku"
        internal const val SEARCH_TARGET = "roku:ecp"
        internal const val POWER_ON = "PowerOn"
        private const val PROBE_TIMEOUT_MS = 2_000L
    }
}
