package io.github.throughthewind.zapper.remote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.throughthewind.zapper.core.designsystem.components.ConnectionChipState
import io.github.throughthewind.zapper.core.designsystem.components.ConnectionStatusChip
import io.github.throughthewind.zapper.core.designsystem.components.TextInputBar
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteHaptic
import io.github.throughthewind.zapper.core.designsystem.theme.rememberRemoteHaptics
import io.github.throughthewind.zapper.core.remote.ConnectionState

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    brandName: (String) -> String,
    onAddTv: () -> Unit,
    onNoTvLeft: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val saved by viewModel.savedDevices.collectAsStateWithLifecycle()
    val defaultId by viewModel.defaultDeviceId.collectAsStateWithLifecycle()
    val volumeKeys by viewModel.hardwareVolumeKeys.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val textInput by viewModel.textInput.collectAsStateWithLifecycle()
    val tvKeyboard by viewModel.tvKeyboard.collectAsStateWithLifecycle()
    var switcher by rememberSaveable { mutableStateOf(false) }
    val offline = state is ConnectionState.Failed || state == ConnectionState.Disconnected
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberRemoteHaptics()

    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { outcome ->
            when (outcome) {
                RemoteFeedback.Success -> {
                    haptics.perform(RemoteHaptic.Success)
                }

                is RemoteFeedback.Failure -> {
                    haptics.perform(RemoteHaptic.Failure)
                    snackbar.showSnackbar(outcome.message)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        RemoteContent(
            viewModel = viewModel,
            state = state,
            actions = remember(state, capabilities) { viewModel.actions(connected = !offline, capabilities = capabilities) },
            onChooseTv = { switcher = true },
        )
        if (textInput) {
            TextInputOverlay(
                tvKeyboard = tvKeyboard,
                onSend = viewModel::sendText,
                onDismiss = viewModel::closeTextInput,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }
    BackHandler(enabled = textInput) { viewModel.closeTextInput() }

    if (switcher) {
        TvSwitcher(
            devices = saved,
            activeId = device?.id,
            activeStatus = state.chip().label(),
            defaultId = defaultId,
            brandName = brandName,
            onSelect = {
                switcher = false
                viewModel.switchTo(it)
            },
            onAction = { tv, action -> viewModel.act(tv, action, onCurrentForgotten = onNoTvLeft) },
            onAddTv = {
                switcher = false
                onAddTv()
            },
            volumeKeys = volumeKeys,
            onVolumeKeysChange = viewModel::setHardwareVolumeKeys,
            onDismiss = { switcher = false },
        )
    }
}

@Composable
private fun RemoteContent(
    viewModel: RemoteViewModel,
    state: ConnectionState,
    actions: RemoteActions,
    onChooseTv: () -> Unit,
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClickLabel = "Choose a TV", onClick = onChooseTv),
                ) {
                    device?.model?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(device?.name ?: "No TV", style = MaterialTheme.typography.titleLarge)
                }
                ConnectionStatusChip(state = state.chip(), onRetry = viewModel::retry)
            }
            layout?.let { RemoteCanvas(it, actions, modifier = Modifier.weight(1f)) }
        }
    }
}

/**
 * The text input bar over a scrim, above the phone keyboard (docs/design/components/RemotePages,
 * TextInputBar). Tapping the scrim closes it, as Back does.
 */
@Composable
private fun TextInputOverlay(
    tvKeyboard: Boolean,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        TextInputBar(
            value = text,
            onValueChange = { text = it },
            onSend = { onSend(text) },
            status = if (tvKeyboard) "TV keyboard open · sends when you tap Send" else "Open a search field on your TV first",
            modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
        )
    }
}

private fun ConnectionState.chip(): ConnectionChipState =
    when (this) {
        ConnectionState.Connected -> ConnectionChipState.Connected
        ConnectionState.Connecting, is ConnectionState.AwaitingPairing -> ConnectionChipState.Connecting
        ConnectionState.Disconnected, is ConnectionState.Failed -> ConnectionChipState.Offline
    }

private fun ConnectionChipState.label(): String =
    when (this) {
        ConnectionChipState.Connected -> "Connected"
        ConnectionChipState.Connecting -> "Connecting"
        ConnectionChipState.Offline -> "Not found"
    }
