package io.github.throughthewind.zapper.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.model.TvDevice

/** What the user asked to do with a saved TV from the switcher. */
sealed interface SavedTvAction {
    data class Rename(
        val name: String,
    ) : SavedTvAction

    data object MakeDefault : SavedTvAction

    data object Forget : SavedTvAction
}

/**
 * The TV picker sheet (docs/design/components/TvSwitcher): one row per saved TV, the active one
 * selected, and "Add a TV" last. Each row's menu renames, makes default, shows info or forgets the
 * TV (SPEC FR-3.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvSwitcher(
    devices: List<TvDevice>,
    activeId: String?,
    activeStatus: String,
    defaultId: String?,
    brandName: (String) -> String,
    onSelect: (TvDevice) -> Unit,
    onAction: (TvDevice, SavedTvAction) -> Unit,
    onAddTv: () -> Unit,
    volumeKeys: Boolean,
    onVolumeKeysChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Your TVs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            devices.forEach { tv ->
                val active = tv.id == activeId
                SavedTvRow(
                    tv = tv,
                    active = active,
                    status =
                        listOfNotNull(
                            if (active) activeStatus else "Saved",
                            "Default".takeIf { tv.id == defaultId },
                        ).joinToString(" · "),
                    brand = brandName(tv.adapterId),
                    onClick = { if (!active) onSelect(tv) },
                    onAction = { onAction(tv, it) },
                )
            }
            ListItem(
                headlineContent = { Text("Add a TV", color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable(onClick = onAddTv),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // The one setting before the settings screen arrives (WI-011).
            ListItem(
                headlineContent = { Text("Phone volume keys control the TV") },
                supportingContent = { Text("While a TV is connected and Zapper is open") },
                trailingContent = { Switch(checked = volumeKeys, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.toggleable(value = volumeKeys, role = Role.Switch, onValueChange = onVolumeKeysChange),
            )
        }
    }
}

private enum class Dialog { None, Rename, Info, Forget }

@Composable
private fun SavedTvRow(
    tv: TvDevice,
    active: Boolean,
    status: String,
    brand: String,
    onClick: () -> Unit,
    onAction: (SavedTvAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(Dialog.None) }
    val colors = MaterialTheme.colorScheme

    ListItem(
        headlineContent = { Text(tv.name) },
        supportingContent = { Text(status) },
        leadingContent = {
            Icon(
                if (active) Icons.Rounded.Check else Icons.Rounded.Tv,
                contentDescription = if (active) "Current TV" else null,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${tv.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menu = false
                        dialog = Dialog.Rename
                    })
                    DropdownMenuItem(text = { Text("Open on this TV") }, onClick = {
                        menu = false
                        onAction(SavedTvAction.MakeDefault)
                    })
                    DropdownMenuItem(text = { Text("TV info") }, onClick = {
                        menu = false
                        dialog = Dialog.Info
                    })
                    DropdownMenuItem(text = { Text("Forget") }, onClick = {
                        menu = false
                        dialog = Dialog.Forget
                    })
                }
            }
        },
        colors =
            ListItemDefaults.colors(
                containerColor = if (active) colors.secondaryContainer else colors.surfaceContainerLow,
            ),
        modifier = Modifier.clickable(onClick = onClick),
    )

    when (dialog) {
        Dialog.None -> {
            Unit
        }

        Dialog.Rename -> {
            RenameDialog(tv.name, onDismiss = { dialog = Dialog.None }) {
                dialog = Dialog.None
                onAction(SavedTvAction.Rename(it))
            }
        }

        Dialog.Info -> {
            AlertDialog(
                onDismissRequest = { dialog = Dialog.None },
                title = { Text(tv.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoLine("Brand", brand)
                        tv.model?.let { InfoLine("Model", it) }
                        InfoLine("IP address", tv.host)
                        tv.macAddress?.let { InfoLine("MAC address", it) }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = Dialog.None }) { Text("Close") } },
            )
        }

        Dialog.Forget -> {
            AlertDialog(
                onDismissRequest = { dialog = Dialog.None },
                title = { Text("Forget ${tv.name}?") },
                text = { Text("Zapper will need to be allowed on this TV again to control it.") },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = Dialog.None
                        onAction(SavedTvAction.Forget)
                    }) { Text("Forget") }
                },
                dismissButton = { TextButton(onClick = { dialog = Dialog.None }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename TV") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MAX_NAME_LENGTH = 40
