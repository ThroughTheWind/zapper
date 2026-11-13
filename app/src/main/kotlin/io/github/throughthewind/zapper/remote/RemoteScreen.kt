package io.github.throughthewind.zapper.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.throughthewind.zapper.core.designsystem.components.ConnectionChipState
import io.github.throughthewind.zapper.core.designsystem.components.ConnectionStatusChip
import io.github.throughthewind.zapper.core.designsystem.components.KeyEmphasis
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.LayoutButton
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.designsystem.components.RemoteKey as KeyButton

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    brandName: (String) -> String,
    onAddTv: () -> Unit,
    onNoTvLeft: () -> Unit,
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val saved by viewModel.savedDevices.collectAsStateWithLifecycle()
    val defaultId by viewModel.defaultDeviceId.collectAsStateWithLifecycle()
    var switcher by rememberSaveable { mutableStateOf(false) }
    val offline = state is ConnectionState.Failed || state == ConnectionState.Disconnected

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClickLabel = "Choose a TV") { switcher = true },
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
            layout?.let { RemoteGrid(it, enabled = !offline, onPress = viewModel::press) }
        }
    }

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
            onDismiss = { switcher = false },
        )
    }
}

@Composable
private fun RemoteGrid(
    layout: RemoteLayout,
    enabled: Boolean,
    onPress: (ButtonAction) -> Unit,
) {
    val rows = layout.buttons.groupBy { it.row }
    val rowCount = (layout.buttons.maxOfOrNull { it.row } ?: -1) + 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rowCount) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(layout.columns) { column ->
                    val button = rows[row]?.firstOrNull { it.column == column }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1.6f)) {
                        if (button != null) {
                            KeyButton(
                                label = button.title(),
                                onPress = { onPress(button.action) },
                                emphasis = button.emphasis(),
                                repeatOnHold = button.repeats(),
                                enabled = enabled,
                                modifier = Modifier.fillMaxSize(),
                            ) { Text(button.title(), style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
        }
    }
}

private fun LayoutButton.title(): String =
    label ?: when (val a = action) {
        is ButtonAction.Key -> a.key.name.replace('_', ' ')
        is ButtonAction.LaunchApp -> a.appId
        is ButtonAction.Macro -> a.keys.joinToString(" ")
    }

private fun LayoutButton.emphasis(): KeyEmphasis =
    when ((action as? ButtonAction.Key)?.key) {
        RemoteKey.POWER -> KeyEmphasis.Power
        RemoteKey.OK -> KeyEmphasis.Primary
        else -> KeyEmphasis.Standard
    }

private val repeatingKeys =
    setOf(
        RemoteKey.VOLUME_UP,
        RemoteKey.VOLUME_DOWN,
        RemoteKey.CHANNEL_UP,
        RemoteKey.CHANNEL_DOWN,
        RemoteKey.UP,
        RemoteKey.DOWN,
        RemoteKey.LEFT,
        RemoteKey.RIGHT,
    )

private fun LayoutButton.repeats(): Boolean = (action as? ButtonAction.Key)?.key in repeatingKeys

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
