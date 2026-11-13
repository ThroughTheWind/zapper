package io.github.throughthewind.zapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.throughthewind.zapper.core.designsystem.components.KeyEmphasis
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.LayoutButton
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.designsystem.components.RemoteKey as KeyButton

@Composable
fun RemoteScreen(viewModel: RemoteViewModel) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    var host by rememberSaveable { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("Samsung TV IP address") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { viewModel.connect(host) }, enabled = host.isNotEmpty()) { Text("Connect") }
            }
            Text(connection.describe(), style = MaterialTheme.typography.bodyMedium)
            layout?.let { RemoteGrid(it, onPress = viewModel::press) }
        }
    }
}

@Composable
private fun RemoteGrid(
    layout: RemoteLayout,
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

private fun ConnectionState.describe(): String =
    when (this) {
        ConnectionState.Disconnected -> "Not connected"
        ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.AwaitingPairing -> "Accept the connection on your TV"
        ConnectionState.Connected -> "Connected"
        is ConnectionState.Failed -> "Failed: $reason"
    }
