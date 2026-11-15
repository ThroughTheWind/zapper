package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteHaptic
import io.github.throughthewind.zapper.core.designsystem.theme.rememberRemoteHaptics
import kotlinx.coroutines.delay

enum class ConnectionChipState { Connected, Connecting, Offline, TvOff }

/** How long "Connected" stays spelled out before the chip shrinks to its dot. */
private const val CONNECTED_LABEL_MS = 3_000L

/**
 * The status chip in the remote's top bar (docs/design/components/ConnectionStatus): Connected
 * (tertiary dot, label hides after 3 s), Connecting (spinner), Offline ("Offline · Retry", tap
 * retries, one Failure haptic when the connection drops), and TV off ("TV off · Power on", tap
 * turns the TV on) when the TV is known but asleep.
 */
@Composable
fun ConnectionStatusChip(
    state: ConnectionChipState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onPowerOn: () -> Unit = onRetry,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberRemoteHaptics()
    var showConnectedLabel by remember { mutableStateOf(true) }
    var wasConnected by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            ConnectionChipState.Connected -> {
                wasConnected = true
                showConnectedLabel = true
                delay(CONNECTED_LABEL_MS)
                showConnectedLabel = false
            }

            ConnectionChipState.Offline, ConnectionChipState.TvOff -> {
                if (wasConnected) haptics.perform(RemoteHaptic.Failure)
                wasConnected = false
            }

            ConnectionChipState.Connecting -> {
                Unit
            }
        }
    }

    val (container, content) =
        when (state) {
            ConnectionChipState.Offline -> colors.errorContainer to colors.onErrorContainer
            ConnectionChipState.TvOff -> colors.secondaryContainer to colors.onSecondaryContainer
            else -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        }
    val description =
        when (state) {
            ConnectionChipState.Connected -> "Connected"
            ConnectionChipState.Connecting -> "Connecting"
            ConnectionChipState.Offline -> "Offline, tap to retry"
            ConnectionChipState.TvOff -> "TV off, tap to turn it on"
        }
    Surface(
        color = container,
        contentColor = content,
        shape = CircleShape,
        modifier =
            modifier
                .semantics(mergeDescendants = true) { contentDescription = description }
                .then(
                    when (state) {
                        ConnectionChipState.Offline -> Modifier.clickable(role = Role.Button, onClick = onRetry)
                        ConnectionChipState.TvOff -> Modifier.clickable(role = Role.Button, onClick = onPowerOn)
                        else -> Modifier
                    },
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            when (state) {
                ConnectionChipState.Connected -> {
                    Surface(color = colors.tertiary, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                    AnimatedVisibility(showConnectedLabel) { Text("Connected", style = MaterialTheme.typography.labelMedium) }
                }

                ConnectionChipState.Connecting -> {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = content)
                    Text("Connecting", style = MaterialTheme.typography.labelMedium)
                }

                ConnectionChipState.Offline -> {
                    Text("Offline · Retry", style = MaterialTheme.typography.labelMedium)
                }

                ConnectionChipState.TvOff -> {
                    Text("TV off · Power on", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
