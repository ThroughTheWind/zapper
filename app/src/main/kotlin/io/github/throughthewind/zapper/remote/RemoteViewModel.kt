package io.github.throughthewind.zapper.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.throughthewind.zapper.AppContainer
import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.TvConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The remote screen: the current TV, its connection, the saved TVs and the layout to draw. */
class RemoteViewModel(
    private val repository: ZapperRepository,
    private val connection: TvConnection,
) : ViewModel() {
    val layout: StateFlow<RemoteLayout?> =
        repository.layouts.map { it.firstOrNull() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val savedDevices: StateFlow<List<TvDevice>> =
        repository.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val defaultDeviceId: StateFlow<String?> =
        repository.defaultDeviceId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The current TV, with the name the user gave it. */
    val device: StateFlow<TvDevice?> =
        combine(connection.device, repository.devices) { current, saved ->
            current?.let { tv -> saved.firstOrNull { it.id == tv.id } ?: tv }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), connection.device.value)

    val state: StateFlow<ConnectionState> = connection.state

    /** Not known, so the first press of a toggle-less TV pauses: people reach for it mid-show. */
    private var playing = true

    /** The canvas's view of the connection; a new one per connection state, so keys re-enable. */
    fun actions(connected: Boolean): RemoteActions =
        object : RemoteActions {
            override fun send(key: RemoteKey) {
                if (key == RemoteKey.PLAY_PAUSE && !connection.supports(RemoteKey.PLAY_PAUSE)) {
                    // A TV with separate Play and Pause keys: alternate between them.
                    connection.send(if (playing) RemoteKey.PAUSE else RemoteKey.PLAY)
                    playing = !playing
                } else {
                    connection.send(key)
                }
            }

            override fun press(action: ButtonAction) = connection.press(action)

            override fun isEnabled(key: RemoteKey): Boolean =
                connected &&
                    (
                        connection.supports(key) ||
                            (key == RemoteKey.PLAY_PAUSE && connection.supports(RemoteKey.PLAY) && connection.supports(RemoteKey.PAUSE))
                    )
        }

    fun retry() {
        connection.device.value?.let(connection::connect)
    }

    fun switchTo(tv: TvDevice) = connection.connect(tv)

    /** Applies a saved-TV action; [onCurrentForgotten] runs when the current TV was forgotten. */
    fun act(
        tv: TvDevice,
        action: SavedTvAction,
        onCurrentForgotten: () -> Unit,
    ) {
        viewModelScope.launch {
            when (action) {
                is SavedTvAction.Rename -> {
                    repository.renameDevice(tv.id, action.name)
                }

                SavedTvAction.MakeDefault -> {
                    repository.setDefaultDevice(tv.id)
                }

                SavedTvAction.Forget -> {
                    val current = connection.device.value?.id == tv.id
                    if (current) connection.disconnect()
                    repository.forgetDevice(tv.id)
                    if (current) onCurrentForgotten()
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) =
            viewModelFactory { initializer { RemoteViewModel(container.repository, container.connection) } }
    }
}
