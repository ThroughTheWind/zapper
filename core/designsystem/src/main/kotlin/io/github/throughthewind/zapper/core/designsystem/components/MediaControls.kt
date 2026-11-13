package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteGrid

enum class MediaControl { Rewind, PlayPause, FastForward }

/**
 * The transport bar (docs/design/components/MediaControls): Standard rewind, a Primary pill
 * play/pause two cells wide (one when [compact]), Standard fast-forward. Rewind and fast-forward
 * repeat on hold. Play/pause is one toggle because most TVs do not report playback state.
 */
@Composable
fun MediaControls(
    onPress: (MediaControl) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: (MediaControl) -> Boolean = { true },
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(RemoteGrid.gutter)) {
        RemoteKey(
            label = "Rewind",
            onPress = { onPress(MediaControl.Rewind) },
            enabled = enabled(MediaControl.Rewind),
            repeatOnHold = true,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { Icon(Icons.Rounded.FastRewind, contentDescription = null) }
        RemoteKey(
            label = "Play or pause",
            onPress = { onPress(MediaControl.PlayPause) },
            emphasis = KeyEmphasis.Primary,
            shape = CircleShape,
            enabled = enabled(MediaControl.PlayPause),
            modifier = Modifier.weight(if (compact) 1f else 2f).fillMaxHeight(),
        ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null) }
        RemoteKey(
            label = "Fast forward",
            onPress = { onPress(MediaControl.FastForward) },
            enabled = enabled(MediaControl.FastForward),
            repeatOnHold = true,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { Icon(Icons.Rounded.FastForward, contentDescription = null) }
    }
}
