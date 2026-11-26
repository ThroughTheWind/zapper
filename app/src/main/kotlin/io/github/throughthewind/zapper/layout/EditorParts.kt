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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.R
import io.github.throughthewind.zapper.core.model.DPadWidget
import io.github.throughthewind.zapper.core.model.DefaultLayouts
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
@Composable
@ReadOnlyComposable
internal fun widgetTitle(widget: LayoutWidget): String =
    when (widget) {
        is LayoutButton -> {
            widget.title()
        }

        is DPadWidget -> {
            stringResource(R.string.widget_dpad)
        }

        is RockerWidget -> {
            val volume = widget.kind == RockerKind.VOLUME
            stringResource(if (volume) R.string.widget_volume_rocker else R.string.widget_channel_rocker)
        }

        is NumberPadWidget -> {
            stringResource(R.string.widget_number_pad)
        }

        is MediaBarWidget -> {
            stringResource(R.string.widget_media_controls)
        }

        is SpacerWidget -> {
            stringResource(R.string.widget_spacer)
        }

        is LabelWidget -> {
            stringResource(R.string.widget_label_with_text, widget.text)
        }

        is UnsupportedWidget -> {
            stringResource(R.string.widget_unsupported)
        }
    }

/**
 * A page's name as shown. The default layout's pages are stored with English names; while the user
 * has not renamed one, it shows in the app's language.
 */
@Composable
@ReadOnlyComposable
internal fun RemotePage.displayName(): String {
    val default = DefaultLayouts.standard.pages.firstOrNull { it.id == id && it.name == name } ?: return name
    return when (default.id) {
        "main" -> stringResource(R.string.page_main)
        "apps" -> stringResource(R.string.page_apps)
        "numbers" -> stringResource(R.string.page_numbers)
        else -> name
    }
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
            val name = page.displayName()
            val currentDescription = stringResource(R.string.page_current_description, name)
            Box {
                Surface(
                    onClick = { if (current) menuFor = page.id else onSelect(page.id) },
                    shape = CircleShape,
                    color = if (current) colors.primary else colors.surfaceContainerHigh,
                    contentColor = if (current) colors.onPrimary else colors.onSurface,
                    modifier =
                        Modifier.semantics {
                            selected = current
                            contentDescription = if (current) currentDescription else name
                        },
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                DropdownMenu(expanded = menuFor == page.id, onDismissRequest = { menuFor = null }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = {
                        menuFor = null
                        onAction(page, PageAction.Rename)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_move_left)) }, enabled = index > 0, onClick = {
                        menuFor = null
                        onAction(page, PageAction.Move(-1))
                    })
                    val moveRight = stringResource(R.string.action_move_right)
                    DropdownMenuItem(text = { Text(moveRight) }, enabled = index < pages.lastIndex, onClick = {
                        menuFor = null
                        onAction(page, PageAction.Move(1))
                    })
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.action_delete),
                                color = if (pages.size > 1) colors.error else colors.onSurface.copy(alpha = 0.38f),
                            )
                        },
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
            Text(stringResource(R.string.page_add), modifier = Modifier.padding(start = 4.dp))
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
                stringResource(if (hovered) R.string.trash_release else R.string.trash_drag_here),
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
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) } },
    )
}

private const val MAX_PAGE_NAME = 20
