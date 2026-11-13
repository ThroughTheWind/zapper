package io.github.throughthewind.zapper.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.github.throughthewind.zapper.core.remote.ConnectionState

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
            val actions = remember(state) { viewModel.actions(connected = !offline) }
            layout?.let { RemoteCanvas(it, actions, modifier = Modifier.weight(1f)) }
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
