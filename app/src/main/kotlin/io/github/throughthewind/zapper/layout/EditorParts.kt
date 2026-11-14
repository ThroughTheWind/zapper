package io.github.throughthewind.zapper.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.model.DPadWidget
import io.github.throughthewind.zapper.core.model.LabelWidget
import io.github.throughthewind.zapper.core.model.LayoutButton
import io.github.throughthewind.zapper.core.model.LayoutWidget
import io.github.throughthewind.zapper.core.model.MediaBarWidget
import io.github.throughthewind.zapper.core.model.NumberPadWidget
import io.github.throughthewind.zapper.core.model.RemotePage
import io.github.throughthewind.zapper.core.model.RockerKind
import io.github.throughthewind.zapper.core.model.RockerWidget
import io.github.throughthewind.zapper.core.model.SpacerWidget
import io.github.throughthewind.zapper.core.model.UnsupportedWidget
import io.github.throughthewind.zapper.remote.title

/** How edit mode names a widget, on screen and to TalkBack. */
internal fun widgetTitle(widget: LayoutWidget): String =
    when (widget) {
        is LayoutButton -> widget.title()
        is DPadWidget -> "D-pad"
        is RockerWidget -> if (widget.kind == RockerKind.VOLUME) "Volume rocker" else "Channel rocker"
        is NumberPadWidget -> "Number pad"
        is MediaBarWidget -> "Media controls"
        is SpacerWidget -> "Spacer"
        is LabelWidget -> "Label ${widget.text}"
        is UnsupportedWidget -> "Unsupported button"
    }

/** What a page bar menu asked for. */
sealed interface PageAction {
    data object Rename : PageAction

    data class Move(
        val by: Int,
    ) : PageAction

    data object Delete : PageAction
}

/**
 * The page bar under the top bar in edit mode (docs/design/components/PageManager): a pill per
 * page, the current one `primary`, then "+ Page". Tapping the current page again opens Rename,
 * Move left, Move right and Delete.
 */
@Composable
fun PageBar(
    pages: List<RemotePage>,
    currentId: String,
    onSelect: (String) -> Unit,
    onAction: (RemotePage, PageAction) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var menuFor by remember { mutableStateOf<String?>(null) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
    ) {
        pages.forEachIndexed { index, page ->
            val current = page.id == currentId
            Box {
                Surface(
                    onClick = { if (current) menuFor = page.id else onSelect(page.id) },
                    shape = CircleShape,
                    color = if (current) colors.primary else colors.surfaceContainerHigh,
                    contentColor = if (current) colors.onPrimary else colors.onSurface,
                    modifier =
                        Modifier.semantics {
                            selected = current
                            contentDescription = if (current) "${page.name}, current page. Tap for options" else page.name
                        },
                ) {
                    Text(
                        page.name,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                DropdownMenu(expanded = menuFor == page.id, onDismissRequest = { menuFor = null }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menuFor = null
                        onAction(page, PageAction.Rename)
                    })
                    DropdownMenuItem(text = { Text("Move left") }, enabled = index > 0, onClick = {
                        menuFor = null
                        onAction(page, PageAction.Move(-1))
                    })
                    DropdownMenuItem(text = { Text("Move right") }, enabled = index < pages.lastIndex, onClick = {
                        menuFor = null
                        onAction(page, PageAction.Move(1))
                    })
                    DropdownMenuItem(
                        text = { Text("Delete", color = if (pages.size > 1) colors.error else colors.onSurface.copy(alpha = 0.38f)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        enabled = pages.size > 1,
                        onClick = {
                            menuFor = null
                            onAction(page, PageAction.Delete)
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text("Page", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/**
 * Replaces the top bar while a widget is dragged (PageManager: trash zone). Idle: an `error`
 * outline; with the finger over it: `error-container`, "Release to remove".
 */
@Composable
fun TrashZone(
    hovered: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxSize()
            .then(if (hovered) Modifier.background(colors.errorContainer, shape) else Modifier)
            .border(2.dp, colors.error, shape),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = null, tint = if (hovered) colors.onErrorContainer else colors.error)
            Text(
                if (hovered) "Release to remove" else "Drag here to remove",
                style = MaterialTheme.typography.titleSmall,
                color = if (hovered) colors.onErrorContainer else colors.error,
            )
        }
    }
}

/** Asks for a page name, for a new page or a rename. */
@Composable
fun PageNameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_PAGE_NAME) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) } },
    )
}

private const val MAX_PAGE_NAME = 20
