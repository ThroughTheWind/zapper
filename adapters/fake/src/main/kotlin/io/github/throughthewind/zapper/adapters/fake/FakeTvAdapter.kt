package io.github.throughthewind.zapper.adapters.fake

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.CopyOnWriteArrayList

/** How the simulated TV behaves. Tests and the debug build tune it. */
data class FakeTvBehaviour(
    val discoveryDelayMs: Long = 800,
    val pairingDelayMs: Long = 1_500,
    val refusePairing: Boolean = false,
    val unsupportedKeys: Set<RemoteKey> = setOf(RemoteKey.GUIDE),
)

/**
 * A simulated TV for building and testing the UI without a real one (SPEC FR-11.3). Shipped in
 * debug builds only. It is found by discovery, answers [HOST] when added by IP, asks for pairing
 * once, and records every key it receives.
 */
class FakeTvAdapter(
    private val behaviour: FakeTvBehaviour = FakeTvBehaviour(),
) : TvAdapter {
    override val id: String = ID
    override val displayName: String = "Simulated TV"

    /** Every key any session sent, as `KEY:ACTION`, and text as `TEXT:typed`, oldest first. */
    val received: MutableList<String> = CopyOnWriteArrayList()

    /** False simulates a TV that is off or off the network: connecting fails with Unreachable. */
    @Volatile var online: Boolean = true

    /** How many times a session tried to connect. */
    @Volatile var connectAttempts: Int = 0
        private set

    private val sessions = CopyOnWriteArrayList<FakeSession>()

    /** Simulates the TV opening or closing its on-screen keyboard. */
    fun showKeyboard(open: Boolean) {
        sessions.forEach { it.keyboard.value = open }
    }

    /** Simulates the network dropping: every connected session fails with ConnectionLost. */
    fun dropConnections() {
        sessions.forEach { it.drop() }
    }

    private val tv =
        DiscoveredTv(
            adapterId = ID,
            host = HOST,
            name = "Simulated TV",
            model = "Zapper Fake 1",
            macAddress = "02:00:00:00:00:01",
            uniqueId = "uuid:fake-tv-1",
        )

    /** A second simulated TV that pairs with a code, like Android TV, to exercise the PIN step. */
    private val pinTv =
        DiscoveredTv(
            adapterId = ID,
            host = PIN_HOST,
            name = "Simulated TV (code)",
            model = "Zapper Fake 2",
            macAddress = "02:00:00:00:00:02",
            uniqueId = "uuid:fake-tv-2",
        )

    override fun discover(): Flow<DiscoveredTv> =
        flow {
            delay(behaviour.discoveryDelayMs)
            emit(tv)
            emit(pinTv)
        }

    override suspend fun probe(host: String): DiscoveredTv? =
        when (host) {
            HOST -> tv
            PIN_HOST -> pinTv
            else -> null
        }

    override fun createSession(
        device: TvDevice,
        credentials: CredentialStore,
    ): TvSession = FakeSession(device, credentials).also { sessions += it }

    private inner class FakeSession(
        override val device: TvDevice,
        private val credentials: CredentialStore,
    ) : TvSession {
        override val capabilities: StateFlow<Set<Capability>> =
            MutableStateFlow(setOf(Capability.KEYS, Capability.LONG_PRESS, Capability.TEXT_INPUT))

        val keyboard = MutableStateFlow(false)
        override val tvKeyboard: StateFlow<Boolean> = keyboard.asStateFlow()

        private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val state: StateFlow<ConnectionState> = _state.asStateFlow()

        fun drop() {
            if (_state.value == ConnectionState.Connected) _state.value = ConnectionState.Failed(TvError.ConnectionLost())
        }

        override suspend fun connect() {
            connectAttempts++
            _state.value = ConnectionState.Connecting
            if (!online) {
                val error = TvError.Unreachable()
                _state.value = ConnectionState.Failed(error)
                throw TvException(error)
            }
            if (credentials.token(device.id) == null && device.host == PIN_HOST) {
                val accepted = CompletableDeferred<Unit>()
                val request =
                    PairingRequest.EnterCode(length = PIN.length, hex = false) { code ->
                        if (code != PIN) throw TvException(TvError.PairingRefused)
                        accepted.complete(Unit)
                    }
                _state.value = ConnectionState.AwaitingPairing(request)
                accepted.await()
                credentials.saveToken(device.id, "fake-token")
            } else if (credentials.token(device.id) == null) {
                _state.value = ConnectionState.AwaitingPairing(PairingRequest.ConfirmOnTv(timeoutMs = 30_000))
                delay(behaviour.pairingDelayMs)
                if (behaviour.refusePairing) {
                    _state.value = ConnectionState.Failed(TvError.PairingRefused)
                    throw TvException(TvError.PairingRefused)
                }
                credentials.saveToken(device.id, "fake-token")
            }
            _state.value = ConnectionState.Connected
        }

        override fun supports(key: RemoteKey): Boolean = key !in behaviour.unsupportedKeys

        override suspend fun send(
            key: RemoteKey,
            action: KeyAction,
        ) {
            if (_state.value != ConnectionState.Connected) throw TvException(TvError.NotConnected)
            if (!supports(key)) return
            received += "${key.name}:${action.name}"
        }

        override suspend fun sendText(text: String) {
            if (_state.value != ConnectionState.Connected) throw TvException(TvError.NotConnected)
            received += "TEXT:$text"
            keyboard.value = false
        }

        override suspend fun disconnect() {
            keyboard.value = false
            _state.value = ConnectionState.Disconnected
        }
    }

    companion object {
        const val ID = "fake"

        /** TEST-NET-1 address, never a real device. Type it in "Add by IP" to reach the fake TV. */
        const val HOST = "192.0.2.1"

        /** The simulated TV that asks for [PIN]. */
        const val PIN_HOST = "192.0.2.2"
        const val PIN = "1234"
    }
}
