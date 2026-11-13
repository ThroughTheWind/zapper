package io.github.throughthewind.zapper

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.discovery.DiscoveryScreen
import io.github.throughthewind.zapper.discovery.DiscoveryViewModel
import io.github.throughthewind.zapper.pairing.PairingScreen
import io.github.throughthewind.zapper.remote.RemoteScreen
import io.github.throughthewind.zapper.remote.RemoteViewModel
import io.github.throughthewind.zapper.welcome.WelcomeScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The app's screens. Few enough that a state machine is clearer than a navigation library. */
sealed interface Screen {
    data object Loading : Screen

    data object Welcome : Screen

    data object Discovery : Screen

    data object Pairing : Screen

    data object Remote : Screen
}

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    init {
        // A saved TV that needs approving again, or whose identity changed, reopens the pairing flow.
        viewModelScope.launch {
            container.connection.state.collect { state ->
                val mustPair =
                    state is ConnectionState.AwaitingPairing ||
                        (state as? ConnectionState.Failed)?.error == TvError.CertificateChanged
                if (mustPair && _screen.value == Screen.Remote) _screen.value = Screen.Pairing
            }
        }
        viewModelScope.launch {
            val last = container.repository.startDevice.first()
            if (last == null) {
                _screen.value = Screen.Welcome
            } else {
                container.connection.connect(last)
                _screen.value = Screen.Remote
            }
        }
    }

    fun findTv() {
        _screen.value = Screen.Discovery
    }

    fun select(tv: DiscoveredTv) {
        container.connection.connect(tv.toDevice())
        _screen.value = Screen.Pairing
    }

    /** The current TV was forgotten: open another saved TV, or start over. */
    fun noTvLeft() {
        viewModelScope.launch {
            val next =
                container.repository.startDevice.first() ?: container.repository.devices
                    .first()
                    .firstOrNull()
            if (next == null) {
                _screen.value = Screen.Welcome
            } else {
                container.connection.connect(next)
            }
        }
    }

    fun paired() {
        _screen.value = Screen.Remote
    }

    fun retryPairing() {
        container.connection.device.value
            ?.let(container.connection::connect)
    }

    fun pairAgain() {
        container.connection.device.value
            ?.let(container.connection::repair)
    }

    fun chooseAnotherTv() {
        container.connection.disconnect()
        _screen.value = Screen.Discovery
    }

    /** Returns false when there is nowhere to go back to, so the system closes the app. */
    fun back(): Boolean =
        when (_screen.value) {
            Screen.Discovery -> {
                _screen.value = if (container.connection.device.value != null) Screen.Remote else Screen.Welcome
                true
            }

            Screen.Pairing -> {
                chooseAnotherTv()
                true
            }

            else -> {
                false
            }
        }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { AppViewModel(container) } }
    }
}

@Composable
fun ZapperApp(container: AppContainer) {
    val app: AppViewModel = viewModel(factory = AppViewModel.factory(container))
    val screen by app.screen.collectAsStateWithLifecycle()
    BackHandler(enabled = screen == Screen.Discovery || screen == Screen.Pairing) { app.back() }

    when (screen) {
        Screen.Loading -> {
            Unit
        }

        Screen.Welcome -> {
            WelcomeScreen(onFindTv = app::findTv)
        }

        Screen.Discovery -> {
            DiscoveryScreen(
                viewModel =
                    viewModel(
                        factory = viewModelFactory { initializer { DiscoveryViewModel(container.adapters, container.multicastLock) } },
                    ),
                brandName = { id -> container.adapters[id]?.displayName ?: id },
                onSelect = app::select,
            )
        }

        Screen.Pairing -> {
            val device by container.connection.device.collectAsStateWithLifecycle()
            val state by container.connection.state.collectAsStateWithLifecycle()
            val reason by container.connection.repairReason.collectAsStateWithLifecycle()
            PairingScreen(
                device = device,
                state = state,
                repairReason = reason,
                onPaired = app::paired,
                onRetry = app::retryPairing,
                onPairAgain = app::pairAgain,
                onChooseAnotherTv = app::chooseAnotherTv,
            )
        }

        Screen.Remote -> {
            RemoteScreen(
                viewModel = viewModel(factory = RemoteViewModel.factory(container)),
                brandName = { id -> container.adapters[id]?.displayName ?: id },
                onAddTv = app::findTv,
                onNoTvLeft = app::noTvLeft,
            )
        }
    }
}
