package io.github.throughthewind.zapper.connection

import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.AdapterRegistry
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.RepairReason
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.connectOrRepair
import io.github.throughthewind.zapper.core.remote.forgetAndConnect
import io.github.throughthewind.zapper.core.remote.perform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one connection to the current TV, shared by every screen. Lives as long as the process.
 */
class ConnectionController(
    private val adapters: AdapterRegistry,
    private val repository: ZapperRepository,
    private val scope: CoroutineScope,
) {
    private val _device = MutableStateFlow<TvDevice?>(null)
    val device: StateFlow<TvDevice?> = _device.asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _repairReason = MutableStateFlow<RepairReason?>(null)

    /** Why pairing runs again for a TV that was already paired, shown above the pairing hero (FR-2.4). */
    val repairReason: StateFlow<RepairReason?> = _repairReason.asStateFlow()

    private var session: TvSession? = null
    private var stateJob: Job? = null
    private var connectJob: Job? = null

    /**
     * Switches to [device] and connects, pairing if the TV asks. A token the TV rejects is dropped
     * and pairing runs again automatically.
     */
    fun connect(device: TvDevice) =
        start(device, reason = null) { onSession ->
            connectOrRepair(device, adapters.adapterFor(device), repository, onSession, onRepair = { _repairReason.value = it })
        }

    /**
     * Forgets [device]'s credentials and pairs again. Only on the user's request: after the TV's
     * certificate changed, a different machine may be answering at its address.
     */
    fun repair(device: TvDevice) =
        start(device, reason = RepairReason.IdentityChanged) { onSession ->
            forgetAndConnect(device, adapters.adapterFor(device), repository, onSession)
        }

    private fun start(
        device: TvDevice,
        reason: RepairReason?,
        open: suspend (onSession: suspend (TvSession) -> Unit) -> TvSession,
    ) {
        connectJob?.cancel()
        stateJob?.cancel()
        // Set synchronously, so no screen sees the previous TV's state as this one's.
        _device.value = device
        _state.value = ConnectionState.Connecting
        _repairReason.value = reason
        connectJob =
            scope.launch {
                session?.disconnect()
                try {
                    open { newSession ->
                        session?.takeIf { it !== newSession }?.disconnect()
                        stateJob?.cancel()
                        session = newSession
                        stateJob = launch { newSession.state.collect { _state.value = it } }
                    }
                    repository.saveDevice(device)
                    _repairReason.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The session's state already shows the failure as a typed TvError.
                }
            }
    }

    fun supports(key: RemoteKey): Boolean = session?.supports(key) ?: true

    fun press(action: ButtonAction) {
        val current = session ?: return
        scope.launch { runCatching { current.perform(action) } }
    }

    fun send(
        key: RemoteKey,
        action: KeyAction,
    ) {
        val current = session ?: return
        scope.launch { runCatching { current.send(key, action) } }
    }

    fun disconnect() {
        connectJob?.cancel()
        stateJob?.cancel()
        val current = session
        session = null
        scope.launch { current?.disconnect() }
        _state.value = ConnectionState.Disconnected
        _repairReason.value = null
    }
}
