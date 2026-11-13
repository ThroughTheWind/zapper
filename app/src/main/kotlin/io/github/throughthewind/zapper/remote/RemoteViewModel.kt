package io.github.throughthewind.zapper.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.throughthewind.zapper.AppContainer
import io.github.throughthewind.zapper.connection.ConnectionController
import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The remote screen: the current TV, its connection and the layout to draw. */
class RemoteViewModel(
    repository: ZapperRepository,
    private val connection: ConnectionController,
) : ViewModel() {
    val layout: StateFlow<RemoteLayout?> =
        repository.layouts.map { it.firstOrNull() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val device: StateFlow<TvDevice?> = connection.device
    val state: StateFlow<ConnectionState> = connection.state

    fun press(action: ButtonAction) = connection.press(action)

    fun retry() {
        connection.device.value?.let(connection::connect)
    }

    companion object {
        fun factory(container: AppContainer) =
            viewModelFactory { initializer { RemoteViewModel(container.repository, container.connection) } }
    }
}
