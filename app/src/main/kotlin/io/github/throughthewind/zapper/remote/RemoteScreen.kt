package io.github.throughthewind.zapper.remote

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.throughthewind.zapper.R
import io.github.throughthewind.zapper.core.designsystem.components.Banner
import io.github.throughthewind.zapper.core.designsystem.components.BannerKind
import io.github.throughthewind.zapper.core.designsystem.components.ConnectionChipState
import io.github.throughthewind.zapper.core.designsystem.components.ConnectionStatusChip
import io.github.throughthewind.zapper.core.designsystem.components.TextInputBar
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteHaptic
import io.github.throughthewind.zapper.core.designsystem.theme.rememberRemoteHaptics
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.PowerState
import io.github.throughthewind.zapper.core.remote.TvError

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    brandName: (String) -> String,
    onAddTv: () -> Unit,
    onNoTvLeft: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val saved by viewModel.savedDevices.collectAsStateWithLifecycle()
    val defaultId by viewModel.defaultDeviceId.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val textInput by viewModel.textInput.collectAsStateWithLifecycle()
    val tvKeyboard by viewModel.tvKeyboard.collectAsStateWithLifecycle()
    val power by viewModel.power.collectAsStateWithLifecycle()
    val poweringOn by viewModel.poweringOn.collectAsStateWithLifecycle()
    val chip = chipState(state, power, poweringOn, canPowerOn = viewModel.canPowerOn())
    var switcher by rememberSaveable { mutableStateOf(false) }
    val offline = state is ConnectionState.Failed || state == ConnectionState.Disconnected
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberRemoteHaptics()
    val resources = LocalContext.current.resources

    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { outcome ->
            when (outcome) {
                RemoteFeedback.Success -> {
                    haptics.perform(RemoteHaptic.Success)
                }

                is RemoteFeedback.Failure -> {
                    haptics.perform(RemoteHaptic.Failure)
                    snackbar.showSnackbar(outcome.message.resolve(resources))
                }

                RemoteFeedback.NotConnected -> {
                    // One at a time: tapping five dead keys does not queue five snackbars.
                    if (snackbar.currentSnackbarData == null) {
                        val result =
                            snackbar.showSnackbar(
                                resources.getString(R.string.error_not_connected),
                                actionLabel = resources.getString(R.string.action_reconnect),
                            )
                        if (result == SnackbarResult.ActionPerformed) viewModel.retry()
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        RemoteContent(
            viewModel = viewModel,
            chip = chip,
            actions = remember(state, capabilities) { viewModel.actions(connected = !offline, capabilities = capabilities) },
            onChooseTv = { switcher = true },
            onEdit = onEdit,
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
            activeStatus = stringResource(chip.label()),
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
            onSettings = {
                switcher = false
                onSettings()
            },
            onDismiss = { switcher = false },
        )
    }
}

@Composable
private fun RemoteContent(
    viewModel: RemoteViewModel,
    chip: ConnectionChipState,
    actions: RemoteActions,
    onChooseTv: () -> Unit,
    onEdit: () -> Unit,
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val powerOnHelp by viewModel.powerOnHelp.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClickLabel = stringResource(R.string.remote_choose_tv), onClick = onChooseTv),
                ) {
                    device?.model?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(device?.name ?: stringResource(R.string.remote_no_tv), style = MaterialTheme.typography.titleLarge)
                }
                ConnectionStatusChip(state = chip, onRetry = viewModel::retry, onPowerOn = viewModel::turnOn)
                // Edit mode opens only from here, never by a long press (SPEC FR-8.2).
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_layout)) }
            }
            // FR-7.2: shown once, at the top of the canvas, pushing the keys down (Banner).
            powerOnHelp?.let { help ->
                Banner(
                    kind = BannerKind.Info,
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = stringResource(R.string.remote_power_on_failed),
                    body = help.forLanguage(LocalConfiguration.current.locales[0].toLanguageTag()),
                ) {
                    TextButton(onClick = viewModel::dismissPowerOnHelp) { Text(stringResource(R.string.action_got_it)) }
                }
            }
            // ErrorsAndHelp: Offline. The chip turns red and the keys go disabled; this says why and
            // what to do. A TV that is only asleep gets "TV off · Power on" in the chip instead.
            if (chip == ConnectionChipState.Offline) {
                Banner(
                    kind = BannerKind.Error,
                    icon = Icons.Rounded.WifiOff,
                    title = stringResource(R.string.remote_cant_reach, device?.name ?: stringResource(R.string.remote_the_tv)),
                    body = stringResource(R.string.tv_unreachable_body),
                ) {
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
                    TextButton(onClick = onChooseTv) { Text(stringResource(R.string.remote_switch_tv)) }
                }
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
            status = stringResource(if (tvKeyboard) R.string.text_input_tv_keyboard_open else R.string.text_input_open_field_first),
            modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
        )
    }
}

/**
 * The chip for the connection and the TV's power (ConnectionStatus): a TV that can be woken and
 * does not answer, or says it is in standby, reads "TV off · Power on" rather than offline.
 */
private fun chipState(
    state: ConnectionState,
    power: PowerState?,
    poweringOn: Boolean,
    canPowerOn: Boolean,
): ConnectionChipState {
    val asleep = (state as? ConnectionState.Failed)?.error.let { it is TvError.Unreachable || it is TvError.ConnectionLost }
    return when {
        poweringOn -> ConnectionChipState.Connecting
        state == ConnectionState.Connected && power == PowerState.STANDBY -> ConnectionChipState.TvOff
        state == ConnectionState.Connected -> ConnectionChipState.Connected
        state == ConnectionState.Connecting || state is ConnectionState.AwaitingPairing -> ConnectionChipState.Connecting
        canPowerOn && (asleep || power == PowerState.STANDBY) -> ConnectionChipState.TvOff
        else -> ConnectionChipState.Offline
    }
}

@StringRes
private fun ConnectionChipState.label(): Int =
    when (this) {
        ConnectionChipState.Connected -> R.string.tv_status_connected
        ConnectionChipState.Connecting -> R.string.tv_status_connecting
        ConnectionChipState.Offline -> R.string.tv_status_not_found
        ConnectionChipState.TvOff -> R.string.tv_status_off
    }
