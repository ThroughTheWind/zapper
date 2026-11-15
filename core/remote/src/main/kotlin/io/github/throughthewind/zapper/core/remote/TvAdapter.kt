package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.TvDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * One TV brand's protocol. Each implementation lives in its own `:adapters:<brand>` module and
 * depends only on `:core:model` and `:core:remote` (ADR-0002).
 */
interface TvAdapter {
    /** Stable id stored in [TvDevice.adapterId], for example `samsung`. Never change it once shipped. */
    val id: String

    /** Brand name shown to the user. */
    val displayName: String

    /**
     * Emits this brand's TVs as they answer on the local network, until the collector stops.
     * The caller holds whatever the platform needs for multicast while collecting.
     */
    fun discover(): Flow<DiscoveredTv> = emptyFlow()

    /** Asks [host] whether it is one of this brand's TVs. Null when it is not, or does not answer. */
    suspend fun probe(host: String): DiscoveredTv? = null

    /** Whether [powerOn] can wake [device] from standby, for example because its MAC address is known. */
    fun canPowerOn(device: TvDevice): Boolean = false

    /**
     * Sends [device] the wake-up, usually Wake-on-LAN, and returns once it is sent: the TV takes
     * seconds to answer. Works while disconnected (SPEC FR-7.1).
     */
    suspend fun powerOn(device: TvDevice): Unit = throw TvException(TvError.Unsupported("turning the TV on"))

    /** What [device] says about its power, without pairing. Null when it does not answer or does not say (FR-7.3). */
    suspend fun powerState(device: TvDevice): PowerState? = null

    /** How to let this brand's TVs be turned on from the phone, shown once when power-on fails (FR-7.2). */
    val powerOnHelp: String? get() = null

    /** Creates a session for [device]. Does not connect; call [TvSession.connect]. */
    fun createSession(
        device: TvDevice,
        credentials: CredentialStore,
    ): TvSession
}

/** A TV's power as it reports it. */
enum class PowerState { ON, STANDBY }

/** A TV found by discovery or by probing an address, claimed by the adapter [adapterId]. */
data class DiscoveredTv(
    val adapterId: String,
    val host: String,
    val name: String,
    val model: String? = null,
    val macAddress: String? = null,
    /** A stable id such as a UPnP UUID. Discovery merges duplicates on it, then on [macAddress]. */
    val uniqueId: String? = null,
    /** Adapter-specific facts carried into [TvDevice.properties]. */
    val properties: Map<String, String> = emptyMap(),
) {
    /** Identity used to merge the same TV seen twice. */
    val identity: String get() = uniqueId ?: macAddress ?: host

    fun toDevice(): TvDevice =
        TvDevice(
            id = identity,
            name = name,
            host = host,
            adapterId = adapterId,
            model = model,
            macAddress = macAddress,
            properties = properties,
        )
}

/**
 * Where adapters keep pairing secrets (tokens, certificate pins), so the TV does not ask to pair
 * again on every connect. The app's implementation encrypts them at rest (SPEC FR-2.3).
 */
interface CredentialStore {
    suspend fun read(
        deviceId: String,
        name: String,
    ): String?

    suspend fun write(
        deviceId: String,
        name: String,
        value: String,
    )

    /** Forgets one secret of [deviceId]. */
    suspend fun remove(
        deviceId: String,
        name: String,
    )

    /** Forgets every secret of [deviceId]; the next connection pairs from scratch. */
    suspend fun clear(deviceId: String)
}

/** The secret name of the pairing token every adapter that has one stores. */
const val TOKEN = "token"

suspend fun CredentialStore.token(deviceId: String): String? = read(deviceId, TOKEN)

suspend fun CredentialStore.saveToken(
    deviceId: String,
    token: String,
) = write(deviceId, TOKEN, token)
