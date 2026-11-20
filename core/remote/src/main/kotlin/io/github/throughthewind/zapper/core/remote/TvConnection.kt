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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one connection to the current TV, shared by every screen (SPEC F3). Brand-independent, so it
 * is tested on the JVM against the fake adapter.
 *
 * - [connect] opens a session, re-pairing once if the TV rejects the saved token.
 * - A TV that cannot be reached, or a connection that drops, is retried with [retryDelaysMs]
 *   backoff while the app is in the foreground. Pairing outcomes are never retried on their own.
 * - Those background retries are quiet: [state] stays on the failure instead of flickering through
 *   Connecting on every attempt, so a TV that is off reads as off. Only a connect the user asked
 *   for (a TV picked, Retry, a key pressed) shows Connecting again.
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

    private val _power = MutableStateFlow<PowerState?>(null)

    /** What the current TV last said about its power; null when unknown (FR-7.3). */
    val power: StateFlow<PowerState?> = _power.asStateFlow()

    private var powerJob: Job? = null

    private val _tvKeyboard = MutableStateFlow(false)

    /** True while the current TV shows its on-screen keyboard (FR-5.1). */
    val tvKeyboard: StateFlow<Boolean> = _tvKeyboard.asStateFlow()

    @Volatile private var session: TvSession? = null
    private var stateJob: Job? = null
    private var runJob: Job? = null
    private var foreground = true

    /** True while retrying in the background after a failure: the session's Connecting is not shown. */
    @Volatile private var retrying = false

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
        _power.value = null
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
            start(current, reason = null, forget = false, quiet = true)
        }
    }

    fun supports(key: RemoteKey): Boolean = session?.supports(key) ?: true

    /** Whether the current TV can be woken from the phone. */
    fun canPowerOn(): Boolean = _device.value?.let { adapters.adapterFor(it).canPowerOn(it) } ?: false

    /** How to enable power-on on the current TV's brand, if the adapter knows (FR-7.2). */
    fun powerOnHelp(): String? = _device.value?.let { adapters.adapterFor(it).powerOnHelp }

    /**
     * Asks the current TV for its power now and remembers the answer. The last answer can be stale:
     * a TV turned off with its own remote keeps its socket open in network standby, so the
     * connection still reads Connected and the Power key cannot tell on from off without asking.
     */
    suspend fun checkPower(): PowerState? {
        val device = _device.value ?: return null
        val reported = quietly { adapters.adapterFor(device).powerState(device) }
        if (_device.value == device) _power.value = reported
        return reported
    }

    /**
     * Wakes the current TV and waits for it to say it is on, sending the wake-up again every few
     * seconds in case a packet was lost. True once the TV is on; it then connects as usual.
     *
     * A TV still connected in standby gets its Power key first, but only some sets honour it there
     * (research §2.5), so the wake-up follows as for a TV that is not connected. Without a wake-up
     * the key is all there is, and this only waits to see whether it worked.
     */
    suspend fun powerOn(timeoutMs: Long = POWER_ON_TIMEOUT_MS): Boolean {
        val device = _device.value ?: return false
        val adapter = adapters.adapterFor(device)
        val standby = _state.value == ConnectionState.Connected && _power.value == PowerState.STANDBY
        val canWake = adapter.canPowerOn(device)
        if (!canWake && !standby) return false
        if (standby) attempt { it.send(RemoteKey.POWER, KeyAction.CLICK) }
        val on =
            withTimeoutOrNull(timeoutMs) {
                var reported: PowerState? = null
                while (reported != PowerState.ON) {
                    if (canWake) quietly { adapter.powerOn(device) }
                    var checks = 0
                    while (reported != PowerState.ON && checks++ < POWER_CHECKS_PER_WAKE) {
                        delay(POWER_CHECK_MS)
                        reported = quietly { adapter.powerState(device) }
                        if (reported != null) _power.value = reported
                    }
                }
                true
            } ?: false
        if (on && _device.value == device && _state.value != ConnectionState.Connected) connect(device)
        return on
    }

    fun press(action: ButtonAction) = whenConnected { it.perform(action) }

    fun send(
        key: RemoteKey,
        action: KeyAction = KeyAction.CLICK,
    ) = whenConnected {
        it.send(key, action)
        // The TV takes a few seconds to go to standby or wake; ask it afterwards.
        if (key == RemoteKey.POWER) refreshPower(delayMs = POWER_SETTLE_MS)
    }

    /** Asks the current TV for its power state, after [delayMs]. */
    private fun refreshPower(delayMs: Long = 0) {
        val device = _device.value ?: return
        powerJob?.cancel()
        powerJob =
            scope.launch {
                delay(delayMs)
                _power.value = quietly { adapters.adapterFor(device).powerState(device) }
            }
    }

    /** Runs [block], treating any failure but cancellation as no answer. */
    private suspend fun <T> quietly(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /** Types [text] on the TV (FR-5.1). Null when the TV took it, otherwise why not. */
    suspend fun sendText(text: String): TvError? = attempt { it.sendText(text) }

    /** The apps the connected TV lists, for the catalog's Apps group; empty when it lists none or is not connected. */
    suspend fun installedApps(): List<TvApp> {
        val current = session.takeIf { _state.value == ConnectionState.Connected } ?: return emptyList()
        return quietly { current.installedApps() }.orEmpty()
    }

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
        powerJob?.cancel()
    }

    private fun start(
        device: TvDevice,
        reason: RepairReason?,
        forget: Boolean,
        quiet: Boolean = false,
    ) {
        runJob?.cancel()
        stateJob?.cancel()
        val previous = session
        session = null
        val sameTv = _device.value?.id == device.id
        // A quiet retry of the same TV keeps showing why it failed until it connects.
        retrying = quiet && sameTv && _state.value is ConnectionState.Failed
        // Set synchronously, so no screen sees the previous TV's state as this one's.
        _device.value = device
        if (!retrying) _state.value = ConnectionState.Connecting
        _repairReason.value = reason
        _capabilities.value = emptySet()
        _tvKeyboard.value = false
        powerJob?.cancel()
        if (!sameTv) _power.value = null
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
                retrying = false
                _repairReason.value = null
                onConnected(device)
                refreshPower()
                // Hold the connection until it drops, then reconnect.
                val current = session ?: return
                current.state.first { it !is ConnectionState.Connected }
                _state.value = ConnectionState.Failed(TvError.ConnectionLost())
                retrying = true
                delay(retryDelaysMs.first())
                continue
            }

            _state.value = ConnectionState.Failed(error)
            refreshPower()
            if (!retryable(error)) return
            retrying = true
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
                launch {
                    newSession.state.collect {
                        // Failures are set by [run]; a background retry's Connecting would only flicker.
                        val hidden = it is ConnectionState.Failed || (retrying && it == ConnectionState.Connecting)
                        if (!hidden) _state.value = it
                    }
                }
                launch { newSession.capabilities.collect { _capabilities.value = it } }
                launch { newSession.tvKeyboard.collect { _tvKeyboard.value = it } }
            }
    }

    private fun retryable(error: TvError): Boolean = error is TvError.Unreachable || error is TvError.ConnectionLost

    companion object {
        /** 1 s, then doubling to a 15 s ceiling: quick after a blip, quiet while the TV is off. */
        val DEFAULT_RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)

        /** SPEC AC-11 asks for the TV on within 10 s; a little more before giving up. */
        const val POWER_ON_TIMEOUT_MS = 15_000L
        private const val POWER_CHECK_MS = 1_000L
        private const val POWER_CHECKS_PER_WAKE = 3
        private const val POWER_SETTLE_MS = 5_000L
    }
}
