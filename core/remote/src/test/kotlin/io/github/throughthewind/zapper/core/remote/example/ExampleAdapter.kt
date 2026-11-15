package io.github.throughthewind.zapper.core.remote.example

import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.CredentialStore
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import io.github.throughthewind.zapper.core.remote.PairingRequest
import io.github.throughthewind.zapper.core.remote.TvAdapter
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.saveToken
import io.github.throughthewind.zapper.core.remote.token
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The smallest adapter that passes TvSessionContract: the starting point docs/adapters.md walks
 * through. Copy this file into adapters/<brand>, rename "Example", and replace ExampleTvApi with
 * the brand's protocol. It is compiled and tested here so the guide cannot drift from the contract.
 */

/**
 * What the brand's TV understands, standing in for its real protocol client (a WebSocket, HTTP or
 * TLS socket in a real adapter). Every call throws [TvException] with a [TvError], never a raw
 * exception: the UI explains failures by type.
 */
interface ExampleTvApi {
    /** What [host] says about itself, or null when it is not one of this brand's TVs. */
    suspend fun info(host: String): ExampleTvInfo?

    /** Pairs, or reuses [token]; returns the token to keep. Shows a prompt on the TV the first time. */
    suspend fun pair(
        host: String,
        token: String?,
    ): String

    suspend fun sendKey(
        host: String,
        token: String,
        code: String,
    )
}

data class ExampleTvInfo(
    val name: String,
    val model: String,
    val macAddress: String?,
    val uuid: String,
)

class ExampleAdapter(
    private val api: ExampleTvApi,
) : TvAdapter {
    // Stored in every saved TV: never rename it once shipped.
    override val id: String = "example"
    override val displayName: String = "Example"

    // Discovery (SSDP, mDNS) is optional at first: adding a TV by IP address only needs probe().
    override suspend fun probe(host: String): DiscoveredTv? =
        api.info(host)?.let { tv ->
            DiscoveredTv(
                adapterId = id,
                host = host,
                name = tv.name,
                model = tv.model,
                macAddress = tv.macAddress,
                uniqueId = tv.uuid,
            )
        }

    override fun createSession(
        device: TvDevice,
        credentials: CredentialStore,
    ): TvSession = ExampleSession(device, credentials, api)
}

/** Brand key codes. A key you have not confirmed on a real TV maps to null and is never sent. */
internal fun RemoteKey.exampleCode(): String? =
    when (this) {
        RemoteKey.POWER -> "power"
        RemoteKey.HOME -> "home"
        RemoteKey.BACK -> "back"
        RemoteKey.UP -> "up"
        RemoteKey.DOWN -> "down"
        RemoteKey.LEFT -> "left"
        RemoteKey.RIGHT -> "right"
        RemoteKey.OK -> "ok"
        RemoteKey.VOLUME_UP -> "volume_up"
        RemoteKey.VOLUME_DOWN -> "volume_down"
        RemoteKey.MUTE -> "mute"
        else -> null
    }

class ExampleSession(
    override val device: TvDevice,
    private val credentials: CredentialStore,
    private val api: ExampleTvApi,
) : TvSession {
    // Declare only what the TV really does. The UI hides everything else.
    override val capabilities: StateFlow<Set<Capability>> = MutableStateFlow(setOf(Capability.KEYS)).asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var token: String? = null

    override suspend fun connect() {
        _state.value = ConnectionState.Connecting
        try {
            val saved = credentials.token(device.id)
            // No token yet: the pairing screen asks the user to accept the prompt on the TV.
            if (saved == null) _state.value = ConnectionState.AwaitingPairing(PairingRequest.ConfirmOnTv(timeoutMs = PAIRING_TIMEOUT_MS))
            val kept = api.pair(device.host, saved)
            if (kept != saved) credentials.saveToken(device.id, kept)
            token = kept
            _state.value = ConnectionState.Connected
        } catch (e: TvException) {
            _state.value = ConnectionState.Failed(e.error)
            throw e
        }
    }

    override fun supports(key: RemoteKey): Boolean = key.exampleCode() != null

    override suspend fun send(
        key: RemoteKey,
        action: KeyAction,
    ) {
        val current = token
        if (_state.value != ConnectionState.Connected || current == null) throw TvException(TvError.NotConnected)
        val code = key.exampleCode() ?: return
        api.sendKey(device.host, current, code)
    }

    override suspend fun disconnect() {
        token = null
        _state.value = ConnectionState.Disconnected
    }

    private companion object {
        const val PAIRING_TIMEOUT_MS = 30_000L
    }
}
