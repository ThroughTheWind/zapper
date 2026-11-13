package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class DeviceCardState { Found, Connecting, Saved, Unreachable }

/**
 * One TV in discovery results, the TV switcher and Saved TVs (docs/design/components/DeviceCard).
 * Zapper ships no brand logos: the leading tile shows the brand's initial.
 */
@Composable
fun DeviceCard(
    title: String,
    subtitle: String,
    brand: String,
    state: DeviceCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDefault: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Surface(
        onClick = onClick,
        color = colors.surfaceContainerHigh,
        shape = shape,
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (state == DeviceCardState.Unreachable) 0.6f else 1f)
                .then(if (state == DeviceCardState.Connecting) Modifier.border(2.dp, colors.primary, shape) else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = "$title, $subtitle, connect" },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(color = colors.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(brand.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = colors.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (isDefault) {
                        Surface(color = colors.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                "Default",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
            }
            when {
                trailing != null -> trailing()
                state == DeviceCardState.Connecting -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                state == DeviceCardState.Unreachable -> Icon(Icons.Rounded.WifiOff, contentDescription = null)
                else -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
    }
}
