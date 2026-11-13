package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class BannerKind { Neutral, Info, Error }

/**
 * Inline card explaining a state or a one-time step (docs/design/components/Banner). Sits in the
 * layout and pushes content down; never covers keys.
 */
@Composable
fun Banner(
    kind: BannerKind,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (kind) {
            BannerKind.Neutral -> colors.surfaceContainerHigh to colors.onSurface
            BannerKind.Info -> colors.secondaryContainer to colors.onSecondaryContainer
            BannerKind.Error -> colors.errorContainer to colors.onErrorContainer
        }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 28.dp)) { actions() }
        }
    }
}
