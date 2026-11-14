package io.github.throughthewind.zapper.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.throughthewind.zapper.AppContainer
import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.model.AppearanceSettings
import io.github.throughthewind.zapper.core.model.ImportedLayout
import io.github.throughthewind.zapper.core.model.LayoutFile
import io.github.throughthewind.zapper.core.model.LayoutFileException
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.TvConnection
import io.github.throughthewind.zapper.remote.SavedTvAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings (SPEC "Settings screen"): TVs, Layout, Appearance, Feedback, Controls, About. */
class SettingsViewModel(
    private val repository: ZapperRepository,
    private val connection: TvConnection,
) : ViewModel() {
    val appearance: StateFlow<AppearanceSettings> =
        repository.appearance.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    val volumeKeys: StateFlow<Boolean> =
        repository.hardwareVolumeKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val devices: StateFlow<List<TvDevice>> =
        repository.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val defaultDeviceId: StateFlow<String?> =
        repository.defaultDeviceId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The current TV, with the name the user gave it. */
    val device: StateFlow<TvDevice?> =
        combine(connection.device, repository.devices) { current, saved ->
            current?.let { tv -> saved.firstOrNull { it.id == tv.id } ?: tv }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), connection.device.value)

    /** Other saved TVs, to copy a layout from (SPEC FR-8.7). */
    val otherDevices: StateFlow<List<TvDevice>> =
        combine(repository.devices, connection.device) { saved, current -> saved.filter { it.id != current?.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short confirmations and errors for a snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch { repository.updateAppearance(transform) }
    }

    fun setVolumeKeys(enabled: Boolean) {
        viewModelScope.launch { repository.setHardwareVolumeKeys(enabled) }
    }

    /** Restores the default layout on the current TV (SPEC FR-8.6, AC-15). */
    fun resetLayout() {
        val id = connection.device.value?.id ?: return
        viewModelScope.launch {
            repository.resetLayout(id)
            _messages.emit("Layout reset to default")
        }
    }

    fun copyLayoutFrom(tv: TvDevice) {
        val id = connection.device.value?.id ?: return
        viewModelScope.launch {
            repository.copyLayout(fromId = tv.id, toId = id)
            _messages.emit("Copied the layout from ${tv.name}")
        }
    }

    /** The current TV's layout, for export. */
    suspend fun currentLayout(): RemoteLayout = repository.layoutFor(connection.device.value?.id).first()

    /** Reads a layout file and makes it the current TV's layout (SPEC FR-12.2). */
    fun importLayout(text: String) {
        val id = connection.device.value?.id ?: return
        viewModelScope.launch {
            val imported =
                try {
                    LayoutFile.decode(text)
                } catch (e: LayoutFileException) {
                    _messages.emit(e.message ?: "This file cannot be imported")
                    return@launch
                }
            repository.saveLayout(id, imported.layout)
            _messages.emit(imported.summary())
        }
    }

    fun report(message: String) {
        _messages.tryEmit(message)
    }

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
            viewModelFactory { initializer { SettingsViewModel(container.repository, container.connection) } }
    }
}

/** What an import did, in a sentence for the snackbar. */
internal fun ImportedLayout.summary(): String =
    when (unsupported) {
        0 -> "Layout imported"
        1 -> "Layout imported. 1 button this version of Zapper can't run is kept as a placeholder."
        else -> "Layout imported. $unsupported buttons this version of Zapper can't run are kept as placeholders."
    }
