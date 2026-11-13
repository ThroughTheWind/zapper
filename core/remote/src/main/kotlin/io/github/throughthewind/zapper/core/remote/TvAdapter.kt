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

    /** Creates a session for [device]. Does not connect; call [TvSession.connect]. */
    fun createSession(
        device: TvDevice,
        credentials: CredentialStore,
    ): TvSession
}

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

/** Where adapters keep pairing secrets, so the TV does not ask to pair again on every connect. */
interface CredentialStore {
    suspend fun token(deviceId: String): String?

    suspend fun saveToken(
        deviceId: String,
        token: String,
    )

    suspend fun clear(deviceId: String)
}
