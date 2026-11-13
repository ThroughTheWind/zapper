package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.TvDevice

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
     * Returns a [TvDevice] if this adapter can drive the TV that discovery found, or null if the
     * TV is not one of this brand's.
     */
    fun recognise(candidate: DiscoveredTv): TvDevice?

    /** Creates a session for [device]. Does not connect; call [TvSession.connect]. */
    fun createSession(
        device: TvDevice,
        credentials: CredentialStore,
    ): TvSession
}

/**
 * A TV seen on the network by discovery (SSDP, mDNS…), before any adapter claims it.
 * [attributes] carries protocol-specific details such as SSDP headers or mDNS TXT records.
 */
data class DiscoveredTv(
    val host: String,
    val name: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

/** Where adapters keep pairing tokens, so the TV does not ask to pair again on every connect. */
interface CredentialStore {
    suspend fun token(deviceId: String): String?

    suspend fun saveToken(
        deviceId: String,
        token: String,
    )

    suspend fun clear(deviceId: String)
}
