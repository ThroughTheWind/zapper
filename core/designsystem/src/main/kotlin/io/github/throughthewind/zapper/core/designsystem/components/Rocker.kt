package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.designsystem.theme.KeyType

enum class RockerStyle(
    val caption: String,
    val upLabel: String,
    val downLabel: String,
) {
    Volume("VOL", "Volume up", "Volume down"),
    Channel("CH", "Channel up", "Channel down"),
}

/**
 * A vertical pill with an up/down pair and a caption between them (docs/design/components/Rocker).
 * Volume uses plus and minus, channel uses chevrons, so the two read differently at a glance. Both
 * halves repeat while held.
 */
@Composable
fun Rocker(
    style: RockerStyle,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val up = if (style == RockerStyle.Volume) Icons.Rounded.Add else Icons.Rounded.KeyboardArrowUp
    val down = if (style == RockerStyle.Volume) Icons.Rounded.Remove else Icons.Rounded.KeyboardArrowDown
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape, modifier = modifier) {
        Column(
            Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            RemoteKey(
                label = style.upLabel,
                onPress = onUp,
                shape = CircleShape,
                enabled = enabled,
                repeatOnHold = true,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { Icon(up, contentDescription = null, modifier = Modifier.size(28.dp)) }
            Text(
                style.caption,
                style = KeyType.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            RemoteKey(
                label = style.downLabel,
                onPress = onDown,
                shape = CircleShape,
                enabled = enabled,
                repeatOnHold = true,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { Icon(down, contentDescription = null, modifier = Modifier.size(28.dp)) }
        }
    }
}
