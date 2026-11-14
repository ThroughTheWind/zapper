package io.github.throughthewind.zapper.layout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.throughthewind.zapper.core.model.LayoutEditing
import io.github.throughthewind.zapper.core.model.LayoutWidget
import io.github.throughthewind.zapper.core.model.RemotePage
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.settings.ConfirmDialog
import io.github.throughthewind.zapper.settings.CopyFromDialog

private enum class EditorDialog { None, Discard, Reset, CopyFrom, NewPage, RenamePage, DeletePage }

/**
 * Edit mode (docs/design/components/EditMode, CustomiseFlow; SPEC F8). The top bar becomes
 * Undo · Edit layout · Done, with Reset, Copy from another TV, Export and Import in its overflow;
 * the page bar sits under it, and "Add a button" opens the catalog. Done saves; Back asks before
 * discarding changes.
 */
@Composable
fun EditLayoutScreen(
    viewModel: LayoutEditorViewModel,
    onClose: () -> Unit,
) {
    // Load the saved layout once per visit, not again after a rotation.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) {
            viewModel.open()
            opened = true
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val others by viewModel.otherDevices.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf(EditorDialog.None) }
    var pageInDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var overTrash by remember { mutableStateOf(false) }
    var trashBounds by remember { mutableStateOf(Rect.Zero) }
    val context = LocalContext.current
    val pickFile = rememberLayoutFilePicker(onText = viewModel::import, onError = viewModel::report)

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    BackHandler { if (state?.history?.changed == true) dialog = EditorDialog.Discard else onClose() }
    val editor = state ?: return

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .onGloballyPositioned { trashBounds = it.boundsInRoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (dragging) {
                        TrashZone(hovered = overTrash)
                    } else {
                        TopBar(
                            canUndo = editor.history.canUndo,
                            canCopy = others.isNotEmpty(),
                            onUndo = viewModel::undo,
                            onDone = { viewModel.save(onClose) },
                            onReset = { dialog = EditorDialog.Reset },
                            onCopy = { dialog = EditorDialog.CopyFrom },
                            onExport = { shareLayout(context, editor.layout) },
                            onImport = pickFile,
                        )
                    }
                }
                PageBar(
                    pages = editor.layout.pages,
                    currentId = editor.page.id,
                    onSelect = viewModel::selectPage,
                    onAction = { page, action ->
                        pageInDialog = page.id
                        when (action) {
                            PageAction.Rename -> {
                                dialog = EditorDialog.RenamePage
                            }

                            is PageAction.Move -> {
                                viewModel.movePage(page.id, action.by)
                            }

                            PageAction.Delete -> {
                                if (page.widgets.isEmpty()) {
                                    viewModel.deletePage(page.id)
                                } else {
                                    dialog =
                                        EditorDialog.DeletePage
                                }
                            }
                        }
                    },
                    onAdd = { dialog = EditorDialog.NewPage },
                )
                EditCanvas(
                    page = editor.page,
                    columns = editor.layout.columns,
                    selectedId = editor.selectedId,
                    trashBounds = trashBounds,
                    actions =
                        object : EditCanvasActions {
                            override fun select(widgetId: String) {
                                viewModel.select(widgetId)
                                optionsFor = widgetId
                            }

                            override fun move(
                                widgetId: String,
                                column: Int,
                                row: Int,
                            ) = viewModel.move(widgetId, column, row)

                            override fun nudge(
                                widgetId: String,
                                columns: Int,
                                rows: Int,
                            ) = viewModel.nudge(widgetId, columns, rows)

                            override fun resize(
                                widgetId: String,
                                span: LayoutEditing.Span,
                            ) = viewModel.resize(widgetId, span)

                            override fun remove(widgetId: String) = viewModel.remove(widgetId)
                        },
                    onDragging = { isDragging, isOverTrash ->
                        dragging = isDragging
                        overTrash = isOverTrash
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                )
                FilledTonalButton(
                    onClick = { catalogOpen = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add a button")
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (catalogOpen) {
        CatalogSheet(
            items = catalog,
            onPick = { if (viewModel.add(it)) catalogOpen = false },
            onDismiss = { catalogOpen = false },
        )
    }

    optionsFor?.let { id ->
        val widget = editor.page.widgets.firstOrNull { it.id == id }
        if (widget == null) {
            LaunchedEffect(id) { optionsFor = null }
        } else {
            ButtonOptionsSheet(
                widget = widget,
                actions = optionsActions(viewModel, widget),
                onDismiss = { optionsFor = null },
            )
        }
    }

    EditorDialogs(
        dialog = dialog,
        page = editor.layout.pages.firstOrNull { it.id == pageInDialog },
        viewModel = viewModel,
        others = others,
        onClose = onClose,
        dismiss = { dialog = EditorDialog.None },
    )
}

private fun optionsActions(
    viewModel: LayoutEditorViewModel,
    widget: LayoutWidget,
) = object : ButtonOptionsActions {
    override fun update(transform: (LayoutWidget) -> LayoutWidget) = viewModel.update(widget.id, transform)

    override fun resize(span: LayoutEditing.Span) =
        viewModel.resize(widget.id, span).also {
            if (!it) viewModel.report("Not enough room for $span here")
        }

    override fun nudge(
        columns: Int,
        rows: Int,
    ) = viewModel.nudge(widget.id, columns, rows).also { if (!it) viewModel.report("Can't move there") }

    override fun remove() = viewModel.remove(widget.id)
}

@Composable
private fun TopBar(
    canUndo: Boolean,
    canCopy: Boolean,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    onReset: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo") }
        Text("Edit layout", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Reset to default") }, onClick = {
                    menu = false
                    onReset()
                })
                DropdownMenuItem(text = { Text("Copy layout from another TV") }, enabled = canCopy, onClick = {
                    menu = false
                    onCopy()
                })
                DropdownMenuItem(text = { Text("Export layout") }, onClick = {
                    menu = false
                    onExport()
                })
                DropdownMenuItem(text = { Text("Import layout") }, onClick = {
                    menu = false
                    onImport()
                })
            }
        }
        Button(onClick = onDone) { Text("Done") }
    }
}

@Composable
private fun EditorDialogs(
    dialog: EditorDialog,
    page: RemotePage?,
    viewModel: LayoutEditorViewModel,
    others: List<TvDevice>,
    onClose: () -> Unit,
    dismiss: () -> Unit,
) {
    when (dialog) {
        EditorDialog.None -> {
            Unit
        }

        EditorDialog.Discard -> {
            ConfirmDialog(
                title = "Discard your changes?",
                body = "The layout goes back to how it was before you started editing.",
                confirm = "Discard",
                onConfirm = {
                    dismiss()
                    onClose()
                },
                onDismiss = dismiss,
            )
        }

        EditorDialog.Reset -> {
            ConfirmDialog(
                title = "Reset to the default layout?",
                body = "Every page and button goes back to the default. Undo brings your layout back until you tap Done.",
                confirm = "Reset",
                onConfirm = {
                    dismiss()
                    viewModel.resetToDefault()
                },
                onDismiss = dismiss,
            )
        }

        EditorDialog.CopyFrom -> {
            CopyFromDialog(
                devices = others,
                onPick = {
                    dismiss()
                    viewModel.copyFrom(it)
                },
                onDismiss = dismiss,
            )
        }

        EditorDialog.NewPage -> {
            PageNameDialog(title = "New page", initial = "", confirm = "Add", onConfirm = {
                dismiss()
                viewModel.addPage(it)
            }, onDismiss = dismiss)
        }

        EditorDialog.RenamePage -> {
            if (page == null) {
                LaunchedEffect(Unit) { dismiss() }
            } else {
                PageNameDialog(title = "Rename page", initial = page.name, confirm = "Save", onConfirm = {
                    dismiss()
                    viewModel.renamePage(page.id, it)
                }, onDismiss = dismiss)
            }
        }

        EditorDialog.DeletePage -> {
            if (page == null) {
                LaunchedEffect(Unit) { dismiss() }
            } else {
                ConfirmDialog(
                    title = "Delete ${page.name}?",
                    body = "Its ${page.widgets.size} buttons go with it. Undo brings the page back until you tap Done.",
                    confirm = "Delete",
                    onConfirm = {
                        dismiss()
                        viewModel.deletePage(page.id)
                    },
                    onDismiss = dismiss,
                )
            }
        }
    }
}
