package io.github.throughthewind.zapper.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.throughthewind.zapper.AppContainer
import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.model.requiredCapability
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.TvConnection
import io.github.throughthewind.zapper.core.remote.TvError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val capabilities: StateFlow<Set<Capability>> = connection.capabilities

    /** True while the TV shows its own keyboard, so typed text lands in a field (FR-5.1). */
    val tvKeyboard: StateFlow<Boolean> = connection.tvKeyboard

    private val _textInput = MutableStateFlow(false)

    /** Whether the text input bar is open: by the Keyboard key, or because the TV asked for text. */
    val textInput: StateFlow<Boolean> = _textInput.asStateFlow()

    private val _feedback = MutableSharedFlow<RemoteFeedback>(extraBufferCapacity = 4)

    /** One-off outcomes the screen answers with a haptic and, on failure, a snackbar. */
    val feedback: SharedFlow<RemoteFeedback> = _feedback.asSharedFlow()

    init {
        // The bar follows the TV keyboard: it opens when the TV asks for text and closes with it.
        viewModelScope.launch {
            var shown = false
            connection.tvKeyboard.collect { open ->
                if (open) _textInput.value = true
                if (shown && !open) _textInput.value = false
                shown = open
            }
        }
    }

    fun openTextInput() {
        _textInput.value = true
    }

    fun closeTextInput() {
        _textInput.value = false
    }

    /** Sends [text] to the TV; closes the bar when the TV took it. */
    fun sendText(text: String) {
        viewModelScope.launch {
            val error = connection.sendText(text)
            if (error == null) _textInput.value = false
            report(error)
        }
    }

    /** Opens an app; a Success haptic when the TV took it, a snackbar saying why when not (AppShortcut). */
    private fun launchApp(appId: String) {
        viewModelScope.launch { report(connection.launchApp(appId)) }
    }

    private suspend fun report(error: TvError?) {
        _feedback.emit(if (error == null) RemoteFeedback.Success else RemoteFeedback.Failure(error.message))
    }

    val hardwareVolumeKeys: StateFlow<Boolean> =
        repository.hardwareVolumeKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setHardwareVolumeKeys(enabled: Boolean) {
        viewModelScope.launch { repository.setHardwareVolumeKeys(enabled) }
    }

    /** Not known, so the first press of a toggle-less TV pauses: people reach for it mid-show. */
    private var playing = true

    /** The canvas's view of the connection; a new one per connection state, so keys re-enable. */
    fun actions(
        connected: Boolean,
        capabilities: Set<Capability>,
    ): RemoteActions =
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

            override fun press(action: ButtonAction) {
                when (action) {
                    ButtonAction.OpenKeyboard -> openTextInput()
                    is ButtonAction.LaunchApp -> launchApp(action.appId)
                    else -> connection.press(action)
                }
            }

            override fun isAvailable(action: ButtonAction): Boolean =
                when (action) {
                    is ButtonAction.Key -> isEnabled(action.key)
                    is ButtonAction.Macro -> action.keys.all { isEnabled(it) }
                    else -> connected && action.requiredCapability in capabilities
                }

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

/** What became of something the user asked the TV to do. */
sealed interface RemoteFeedback {
    data object Success : RemoteFeedback

    data class Failure(
        val message: String,
    ) : RemoteFeedback
}
