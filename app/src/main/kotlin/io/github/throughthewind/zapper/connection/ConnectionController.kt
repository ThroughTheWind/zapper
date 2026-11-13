package io.github.throughthewind.zapper.connection

import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.AdapterRegistry
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.perform
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

    private var session: TvSession? = null
    private var stateJob: Job? = null
    private var connectJob: Job? = null

    /** Switches to [device] and connects, pairing if the TV asks. */
    fun connect(device: TvDevice) {
        connectJob?.cancel()
        connectJob =
            scope.launch {
                session?.disconnect()
                stateJob?.cancel()
                _device.value = device
                val newSession = adapters.adapterFor(device).createSession(device, repository)
                session = newSession
                stateJob = launch { newSession.state.collect { _state.value = it } }
                runCatching { newSession.connect() }
                    .onSuccess { repository.saveDevice(device) }
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
        val current = session
        session = null
        scope.launch { current?.disconnect() }
        _state.value = ConnectionState.Disconnected
    }
}
