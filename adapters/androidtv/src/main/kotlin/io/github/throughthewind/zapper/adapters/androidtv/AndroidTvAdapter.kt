package io.github.throughthewind.zapper.adapters.androidtv

import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.CredentialStore
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import io.github.throughthewind.zapper.core.remote.TvAdapter
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.security.cert.X509Certificate

/**
 * Android TV and Google TV: Sony, TCL, Hisense, Philips and other TVs, Chromecast with Google TV,
 * Google TV Streamer and Nvidia Shield, over the Android TV Remote protocol v2 (research §3).
 */
class AndroidTvAdapter internal constructor(
    private val clientName: String,
    private val browse: () -> Flow<Mdns.Instance>,
    private val pairingPort: Int,
    private val remotePort: Int,
    private val probeTimeoutMs: Int = PROBE_TIMEOUT_MS,
    private val wake: suspend (mac: String, host: String) -> Unit = WakeOnLan::send,
) : TvAdapter {
    /** [clientName] is how the TV lists this phone among its remotes. */
    constructor(clientName: String = "Zapper") : this(clientName, { Mdns().browse(SERVICE) }, Polo.PORT, Remote.PORT)

    override val id: String = ID
    override val displayName: String = "Android TV / Google TV"

    /** Probing needs some client certificate; one per app run is enough, and is made only when a port answers. */
    private val probeIdentity by lazy { ClientIdentity.generate(clientName) }

    /** mDNS for the remote service; each answer is confirmed, and named, by [probe]. */
    override fun discover(): Flow<DiscoveredTv> {
        val asked = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        return browse()
            .filter { synchronized(asked) { asked.add(it.host) } }
            .mapNotNull { instance -> probe(instance.host)?.let { tv -> instance.name?.let { tv.copy(name = it) } ?: tv } }
            .filter { synchronized(seen) { seen.add(it.identity) } }
    }

    /**
     * A TLS handshake on the pairing port, which only Android TV's remote service listens on. The
     * TV's certificate names it and carries its MAC address, which identifies it and wakes it.
     */
    override suspend fun probe(host: String): DiscoveredTv? =
        withContext(Dispatchers.IO) {
            runCatching {
                val trust = PinningTrustManager(null)
                AndroidTvTls.connect(host, pairingPort, probeTimeoutMs, trust) { probeIdentity }.close()
                trust.seen?.let { describe(host, it) }
            }.getOrNull()
        }

    override fun canPowerOn(device: TvDevice): Boolean = device.macAddress != null

    /**
     * Wake-on-LAN to the MAC from the TV's certificate. Most Google TVs keep the remote service
     * running in standby instead, where the remote's Power key wakes them.
     */
    override suspend fun powerOn(device: TvDevice) {
        val mac = device.macAddress ?: throw TvException(TvError.Unsupported("turning on a TV without its MAC address"))
        wake(mac, device.host)
    }

    override val powerOnHelp: String =
        "On the TV, look under Settings › Network for an option such as Remote start, Wake on LAN or Networked standby, and turn it on."

    override fun createSession(
        device: TvDevice,
        credentials: CredentialStore,
    ): TvSession =
        AndroidTvSession(
            device = device,
            credentials = credentials,
            clientName = clientName,
            remotePort = remotePort,
            pairingPort = pairingPort,
        )

    companion object {
        const val ID = "androidtv"
        internal const val SERVICE = "_androidtvremote2._tcp.local"
        private const val PROBE_TIMEOUT_MS = 2_000
        private val MAC = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

        /**
         * The name and MAC in the TV's certificate subject, as the reference reads them:
         * `CN=atvremote/darcy/darcy/SHIELD Android TV/XX:XX:XX:XX:XX:XX`, or on older devices
         * `dnQualifier=fugu/fugu/Nexus Player, CN=atvremote/XX:XX:XX:XX:XX:XX`.
         */
        internal fun describe(
            host: String,
            certificate: X509Certificate,
        ): DiscoveredTv {
            val names = runCatching { Der.subjectNames(certificate.subjectX500Principal.encoded) }.getOrDefault(emptyMap())
            val common = names["CN"].orEmpty().split('/')
            val qualifier = names["dnQualifier"]
            val model =
                when {
                    !qualifier.isNullOrBlank() -> qualifier.split('/').last()
                    common.size > 1 -> common[common.size - 2]
                    else -> null
                }?.takeIf { it.isNotBlank() && it != "atvremote" }
            val mac = common.last().takeIf { MAC.matches(it) }?.lowercase()
            return DiscoveredTv(
                adapterId = ID,
                host = host,
                name = model ?: "Android TV",
                model = model,
                macAddress = mac,
            )
        }
    }
}
