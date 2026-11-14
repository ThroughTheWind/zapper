package io.github.throughthewind.zapper.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.designsystem.components.KeyEmphasis
import io.github.throughthewind.zapper.core.designsystem.components.colors
import io.github.throughthewind.zapper.core.designsystem.theme.KeyType
import io.github.throughthewind.zapper.core.designsystem.theme.keyShape
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.CatalogGroup
import io.github.throughthewind.zapper.core.model.CatalogItem
import io.github.throughthewind.zapper.core.model.DPadWidget
import io.github.throughthewind.zapper.core.model.LabelWidget
import io.github.throughthewind.zapper.core.model.LayoutButton
import io.github.throughthewind.zapper.core.model.LayoutWidget
import io.github.throughthewind.zapper.core.model.MediaBarWidget
import io.github.throughthewind.zapper.core.model.NumberPadWidget
import io.github.throughthewind.zapper.core.model.RockerKind
import io.github.throughthewind.zapper.core.model.RockerWidget
import io.github.throughthewind.zapper.core.model.SpacerWidget
import io.github.throughthewind.zapper.core.model.UnsupportedWidget
import io.github.throughthewind.zapper.remote.emphasis
import io.github.throughthewind.zapper.remote.icon
import io.github.throughthewind.zapper.remote.keyIcon
import io.github.throughthewind.zapper.remote.title

/**
 * The catalog (docs/design/components/ButtonCatalog): the spec's groups as filter chips, a search
 * across all of them, and 56 dp previews four to a row. Tapping one places it in the first free
 * cell of the current page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogSheet(
    items: List<CatalogItem>,
    onPick: (CatalogItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<CatalogGroup?>(null) }
    val shown =
        items.filter { item ->
            if (query.isNotBlank()) item.title.contains(query.trim(), ignoreCase = true) else group == null || item.group == group
        }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Add a button", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search buttons") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All") })
                CatalogGroup.entries.filter { g -> items.any { it.group == g } }.forEach { g ->
                    FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g.title) })
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).padding(vertical = 12.dp),
            ) {
                items(shown, key = { "${it.group}/${it.title}/${it.widget.id}" }) { item -> CatalogTile(item, onClick = { onPick(item) }) }
            }
            if (shown.isEmpty()) {
                Text(
                    "Nothing matches. This TV may not support it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun CatalogTile(
    item: CatalogItem,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .clickable(role = Role.Button, onClickLabel = "Add", onClick = onClick)
                .semantics { contentDescription = "Add ${item.title}" },
    ) {
        WidgetPreview(item.widget, Modifier.size(56.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** A widget at key size, for the catalog and the options sheet: its icon, monogram or caption on its default colour. */
@Composable
internal fun WidgetPreview(
    widget: LayoutWidget,
    modifier: Modifier = Modifier,
) {
    val button = widget as? LayoutButton
    val action = button?.action
    val emphasis =
        button?.color?.emphasis() ?: when {
            action is ButtonAction.LaunchApp -> KeyEmphasis.Tertiary
            action is ButtonAction.Key -> action.key.emphasis()
            widget is SpacerWidget || widget is LabelWidget -> KeyEmphasis.Ghost
            else -> KeyEmphasis.Standard
        }
    val colors = emphasis.colors()
    Box(
        modifier.clip(keyShape).background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        val icon = previewIcon(widget)
        when {
            action is ButtonAction.LaunchApp -> {
                Text(
                    widgetTitle(widget).take(1).uppercase(),
                    color = colors.content,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            icon != null -> {
                Icon(icon, contentDescription = null, tint = colors.content)
            }

            else -> {
                Text(
                    (button?.title() ?: widgetTitle(widget)).take(3).uppercase(),
                    color = colors.content,
                    style = KeyType.label,
                )
            }
        }
    }
}

private fun previewIcon(widget: LayoutWidget): ImageVector? =
    when (widget) {
        is LayoutButton -> widget.icon?.let(::keyIcon) ?: (widget.action as? ButtonAction.Key)?.key?.icon() ?: widget.action.icon()
        is DPadWidget -> Icons.Rounded.OpenWith
        is RockerWidget -> if (widget.kind == RockerKind.VOLUME) Icons.Rounded.UnfoldMore else Icons.Rounded.SwapVert
        is NumberPadWidget -> Icons.Rounded.Dialpad
        is MediaBarWidget -> Icons.Rounded.PlayCircle
        is SpacerWidget -> Icons.Rounded.SpaceBar
        is LabelWidget -> Icons.Rounded.Title
        is UnsupportedWidget -> null
    }
