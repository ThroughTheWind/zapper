package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The one connection to the current TV, shared by every screen (SPEC F3). Brand-independent, so it
 * is tested on the JVM against the fake adapter.
 *
 * - [connect] opens a session, re-pairing once if the TV rejects the saved token.
 * - A TV that cannot be reached, or a connection that drops, is retried with [retryDelaysMs]
 *   backoff while the app is in the foreground. Pairing outcomes are never retried on their own.
 * - [pause] drops the connection when the app leaves the foreground, [resume] restores it, and
 *   [networkAvailable] retries at once when the phone's network returns (FR-3.4, AC-12).
 * - A press while not connected starts a reconnect and is dropped, never queued (FR-3.5).
 */
class TvConnection(
    private val adapters: AdapterRegistry,
    private val credentials: CredentialStore,
    private val scope: CoroutineScope,
    private val onConnected: suspend (TvDevice) -> Unit = {},
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) {
    private val _device = MutableStateFlow<TvDevice?>(null)
    val device: StateFlow<TvDevice?> = _device.asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _repairReason = MutableStateFlow<RepairReason?>(null)

    /** Why pairing runs again for a TV that was already paired, shown above the pairing hero (FR-2.4). */
    val repairReason: StateFlow<RepairReason?> = _repairReason.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<Capability>>(emptySet())

    /** What the current TV can do. Empty until it has connected once. */
    val capabilities: StateFlow<Set<Capability>> = _capabilities.asStateFlow()

    private val _tvKeyboard = MutableStateFlow(false)

    /** True while the current TV shows its on-screen keyboard (FR-5.1). */
    val tvKeyboard: StateFlow<Boolean> = _tvKeyboard.asStateFlow()

    @Volatile private var session: TvSession? = null
    private var stateJob: Job? = null
    private var runJob: Job? = null
    private var foreground = true

    /** Switches to [device] and connects, pairing if the TV asks. */
    fun connect(device: TvDevice) = start(device, reason = null, forget = false)

    /**
     * Forgets [device]'s credentials and pairs again. Only on the user's request: after the TV's
     * certificate changed, a different machine may be answering at its address.
     */
    fun repair(device: TvDevice) = start(device, reason = RepairReason.IdentityChanged, forget = true)

    /** Stops talking to the TV and forgets which one was current. */
    fun disconnect() {
        stop()
        _device.value = null
    }

    /** The app left the foreground: drop the socket, keep the TV (FR-3.4, NFR-12). */
    fun pause() {
        foreground = false
        stop()
    }

    /** The app is back in the foreground: reconnect to the current TV, if any. */
    fun resume() {
        foreground = true
        val current = _device.value ?: return
        if (_state.value !is ConnectionState.Connected) connect(current)
    }

    /** The phone's network came back: retry now instead of waiting for the backoff. */
    fun networkAvailable() {
        val current = _device.value ?: return
        if (foreground && _state.value is ConnectionState.Failed && retryable((_state.value as ConnectionState.Failed).error)) {
            connect(current)
        }
    }

    fun supports(key: RemoteKey): Boolean = session?.supports(key) ?: true

    fun press(action: ButtonAction) = whenConnected { it.perform(action) }

    fun send(
        key: RemoteKey,
        action: KeyAction = KeyAction.CLICK,
    ) = whenConnected { it.send(key, action) }

    /** Types [text] on the TV (FR-5.1). Null when the TV took it, otherwise why not. */
    suspend fun sendText(text: String): TvError? = attempt { it.sendText(text) }

    /** Opens an app on the TV (FR-6.1). Null when the TV took the request, otherwise why not. */
    suspend fun launchApp(appId: String): TvError? = attempt { it.launchApp(appId) }

    private fun whenConnected(block: suspend (TvSession) -> Unit) {
        val current = connectedSession() ?: return
        scope.launch { runCatching { block(current) } }
    }

    /** Runs [block] now and says why it failed, if it did, for actions the user expects an answer to. */
    private suspend fun attempt(block: suspend (TvSession) -> Unit): TvError? {
        val current = connectedSession() ?: return TvError.NotConnected
        return try {
            block(current)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: TvException) {
            e.error
        } catch (e: Exception) {
            TvError.ConnectionLost()
        }
    }

    /** The session if connected; otherwise starts a reconnect and returns null (FR-3.5: never queue). */
    private fun connectedSession(): TvSession? {
        val current = session
        if (current != null && _state.value == ConnectionState.Connected) return current
        val device = _device.value
        val idle = _state.value is ConnectionState.Failed || _state.value == ConnectionState.Disconnected
        if (device != null && idle && foreground) connect(device)
        return null
    }

    private fun stop() {
        runJob?.cancel()
        stateJob?.cancel()
        val current = session
        session = null
        if (current != null) scope.launch { current.disconnect() }
        _state.value = ConnectionState.Disconnected
        _repairReason.value = null
        _capabilities.value = emptySet()
        _tvKeyboard.value = false
    }

    private fun start(
        device: TvDevice,
        reason: RepairReason?,
        forget: Boolean,
    ) {
        runJob?.cancel()
        stateJob?.cancel()
        val previous = session
        session = null
        // Set synchronously, so no screen sees the previous TV's state as this one's.
        _device.value = device
        _state.value = ConnectionState.Connecting
        _repairReason.value = reason
        _capabilities.value = emptySet()
        _tvKeyboard.value = false
        runJob =
            scope.launch {
                previous?.disconnect()
                run(device, forget)
            }
    }

    private suspend fun run(
        device: TvDevice,
        forgetFirst: Boolean,
    ) {
        val adapter = adapters.adapterFor(device)
        var forget = forgetFirst
        var failures = 0
        while (true) {
            val error =
                try {
                    if (forget) {
                        forgetAndConnect(device, adapter, credentials, ::adopt)
                    } else {
                        connectOrRepair(device, adapter, credentials, ::adopt, onRepair = { _repairReason.value = it })
                    }
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TvException) {
                    e.error
                } catch (e: Exception) {
                    TvError.Unreachable(e.message ?: "The TV did not answer")
                }
            forget = false

            if (error == null) {
                failures = 0
                _repairReason.value = null
                onConnected(device)
                // Hold the connection until it drops, then reconnect.
                val current = session ?: return
                current.state.first { it !is ConnectionState.Connected }
                _state.value = ConnectionState.Failed(TvError.ConnectionLost())
                delay(retryDelaysMs.first())
                continue
            }

            _state.value = ConnectionState.Failed(error)
            if (!retryable(error)) return
            delay(retryDelaysMs[minOf(failures, retryDelaysMs.lastIndex)])
            failures++
        }
    }

    private suspend fun adopt(newSession: TvSession) {
        session?.takeIf { it !== newSession }?.disconnect()
        stateJob?.cancel()
        session = newSession
        stateJob =
            scope.launch {
                launch { newSession.state.collect { if (it !is ConnectionState.Failed) _state.value = it } }
                launch { newSession.capabilities.collect { _capabilities.value = it } }
                launch { newSession.tvKeyboard.collect { _tvKeyboard.value = it } }
            }
    }

    private fun retryable(error: TvError): Boolean = error is TvError.Unreachable || error is TvError.ConnectionLost

    companion object {
        /** 1 s, then doubling to a 15 s ceiling: quick after a blip, quiet while the TV is off. */
        val DEFAULT_RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
    }
}
