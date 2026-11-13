package io.github.throughthewind.zapper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.remote.AdapterRegistry
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.perform
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Scaffold screen logic: connect to a TV by IP address and press buttons from the layout.
 * Replaced by the discovery, pairing and remote screens of WI-003 to WI-006.
 */
class RemoteViewModel(
    private val repository: ZapperRepository,
    private val adapters: AdapterRegistry,
    private val fallbackAdapterId: String,
) : ViewModel() {
    val layout: StateFlow<RemoteLayout?> =
        repository.layouts.map { it.firstOrNull() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private var session: TvSession? = null
    private var stateJob: Job? = null

    fun connect(host: String) {
        viewModelScope.launch {
            session?.disconnect()
            stateJob?.cancel()
            val found = adapters.probe(host) ?: DiscoveredTv(adapterId = fallbackAdapterId, host = host, name = host)
            val device = found.toDevice()
            val newSession = adapters.adapterFor(device).createSession(device, repository)
            session = newSession
            stateJob = launch { newSession.state.collect { _connection.value = it } }
            runCatching { newSession.connect() }
                .onSuccess { repository.saveDevice(device) }
        }
    }

    fun press(action: ButtonAction) {
        val current = session ?: return
        viewModelScope.launch { runCatching { current.perform(action) } }
    }

    override fun onCleared() {
        val current = session ?: return
        // Closing the socket does not block; this only waits for the close frame to be queued.
        runBlocking { current.disconnect() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { RemoteViewModel(container.repository, container.adapters, container.fallbackAdapterId) }
            }
    }
}
