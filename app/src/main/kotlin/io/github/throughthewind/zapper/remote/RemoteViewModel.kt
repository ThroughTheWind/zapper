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
import io.github.throughthewind.zapper.core.remote.PowerState
import io.github.throughthewind.zapper.core.remote.TvConnection
import io.github.throughthewind.zapper.core.remote.TvError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The remote screen: the current TV, its connection, the saved TVs and the layout to draw. */
class RemoteViewModel(
    private val repository: ZapperRepository,
    private val connection: TvConnection,
) : ViewModel() {
    /** The current TV's own layout (SPEC FR-8.7). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val layout: StateFlow<RemoteLayout?> =
        connection.device
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { repository.layoutFor(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /** What the TV last said about its power (FR-7.3). */
    val power: StateFlow<PowerState?> = connection.power

    private val _poweringOn = MutableStateFlow(false)

    /** True while waiting for the TV to wake. */
    val poweringOn: StateFlow<Boolean> = _poweringOn.asStateFlow()

    private val _powerOnHelp = MutableStateFlow<String?>(null)

    /** The one-time explanation of how to let the phone turn the TV on, while it shows (FR-7.2). */
    val powerOnHelp: StateFlow<String?> = _powerOnHelp.asStateFlow()

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

    /** Whether the Power key can wake the current TV while it is not connected. */
    fun canPowerOn(): Boolean = connection.canPowerOn()

    /**
     * Power while the TV is not connected (FR-7.1): wake it and wait. When it stays off, the first
     * time explains how to enable power-on on the TV; later times only say it did not work.
     */
    fun powerOn() {
        if (_poweringOn.value) return
        viewModelScope.launch {
            _poweringOn.value = true
            val on =
                try {
                    connection.powerOn()
                } finally {
                    _poweringOn.value = false
                }
            val help = connection.powerOnHelp()
            when {
                on -> _feedback.emit(RemoteFeedback.Success)
                help != null && !repository.powerOnHelpSeen.first() -> _powerOnHelp.value = help
                else -> _feedback.emit(RemoteFeedback.Failure("The TV did not turn on"))
            }
        }
    }

    /** The status chip's "Power on": a connected TV in standby gets its Power key, any other is woken. */
    fun turnOn() {
        if (connection.state.value == ConnectionState.Connected) connection.send(RemoteKey.POWER) else powerOn()
    }

    fun dismissPowerOnHelp() {
        _powerOnHelp.value = null
        viewModelScope.launch { repository.setPowerOnHelpSeen() }
    }

    /** Opens an app; a Success haptic when the TV took it, a snackbar saying why when not (AppShortcut). */
    private fun launchApp(appId: String) {
        viewModelScope.launch { report(connection.launchApp(appId)) }
    }

    private suspend fun report(error: TvError?) {
        _feedback.emit(if (error == null) RemoteFeedback.Success else RemoteFeedback.Failure(error.message))
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
                if (key == RemoteKey.POWER && !connected) {
                    powerOn()
                } else if (key == RemoteKey.PLAY_PAUSE && !connection.supports(RemoteKey.PLAY_PAUSE)) {
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
                    is ButtonAction.Key -> send(action.key)
                    else -> connection.press(action)
                }
            }

            override fun isAvailable(action: ButtonAction): Boolean =
                when (action) {
                    is ButtonAction.Key -> isEnabled(action.key)
                    is ButtonAction.Macro -> action.keys.all { isEnabled(it) }
                    else -> connected && action.requiredCapability in capabilities
                }

            override fun notSupported() {
                _feedback.tryEmit(RemoteFeedback.Failure("Not supported on this TV"))
            }

            override fun isEnabled(key: RemoteKey): Boolean {
                // Power stays usable while the TV is off, to wake it (ErrorsAndHelp).
                if (!connected) return key == RemoteKey.POWER && canPowerOn()
                return connection.supports(key) ||
                    (key == RemoteKey.PLAY_PAUSE && connection.supports(RemoteKey.PLAY) && connection.supports(RemoteKey.PAUSE))
            }
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
