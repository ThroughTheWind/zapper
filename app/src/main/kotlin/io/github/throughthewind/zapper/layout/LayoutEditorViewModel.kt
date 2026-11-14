package io.github.throughthewind.zapper.layout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.throughthewind.zapper.AppContainer
import io.github.throughthewind.zapper.core.data.ZapperRepository
import io.github.throughthewind.zapper.core.model.ButtonCatalog
import io.github.throughthewind.zapper.core.model.CatalogApp
import io.github.throughthewind.zapper.core.model.CatalogItem
import io.github.throughthewind.zapper.core.model.DefaultLayouts
import io.github.throughthewind.zapper.core.model.EditHistory
import io.github.throughthewind.zapper.core.model.LayoutEditing
import io.github.throughthewind.zapper.core.model.LayoutEditing.addPage
import io.github.throughthewind.zapper.core.model.LayoutEditing.addWidget
import io.github.throughthewind.zapper.core.model.LayoutEditing.deletePage
import io.github.throughthewind.zapper.core.model.LayoutEditing.movePage
import io.github.throughthewind.zapper.core.model.LayoutEditing.moveWidget
import io.github.throughthewind.zapper.core.model.LayoutEditing.page
import io.github.throughthewind.zapper.core.model.LayoutEditing.removeWidget
import io.github.throughthewind.zapper.core.model.LayoutEditing.renamePage
import io.github.throughthewind.zapper.core.model.LayoutEditing.resizeWidget
import io.github.throughthewind.zapper.core.model.LayoutEditing.updateWidget
import io.github.throughthewind.zapper.core.model.LayoutFile
import io.github.throughthewind.zapper.core.model.LayoutFileException
import io.github.throughthewind.zapper.core.model.LayoutWidget
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.model.RemotePage
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.TvConnection
import io.github.throughthewind.zapper.settings.summary
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What edit mode shows: the layout being edited with its undo history, the page and the selected widget. */
data class EditorState(
    val history: EditHistory<RemoteLayout>,
    val pageId: String,
    val selectedId: String? = null,
) {
    val layout: RemoteLayout get() = history.current

    val page: RemotePage get() = layout.page(pageId) ?: layout.pages.first()

    val selected: LayoutWidget? get() = page.widgets.firstOrNull { it.id == selectedId }
}

/**
 * Edit mode (SPEC F8, docs/design/components/EditMode and CustomiseFlow). Works on a copy of the
 * current TV's layout; Done saves it, Back discards it. Every edit can be undone (FR-8.6). The
 * rules (no overlaps, allowed sizes) live in [LayoutEditing].
 */
class LayoutEditorViewModel(
    private val repository: ZapperRepository,
    private val connection: TvConnection,
) : ViewModel() {
    private val _state = MutableStateFlow<EditorState?>(null)

    /** Null until the saved layout is loaded. */
    val state: StateFlow<EditorState?> = _state.asStateFlow()

    private val _catalog = MutableStateFlow<List<CatalogItem>>(emptyList())

    /** What the catalog offers: everything the current TV can run (FR-4.5). */
    val catalog: StateFlow<List<CatalogItem>> = _catalog.asStateFlow()

    /** Other saved TVs, to copy a layout from (FR-8.7). */
    val otherDevices: StateFlow<List<TvDevice>> =
        repository.devices
            .map { saved -> saved.filter { it.id != deviceId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short confirmations and refusals for a snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var deviceId: String? = connection.device.value?.id

    /** Starts editing the current TV's saved layout, dropping any unsaved edits. */
    fun open() {
        deviceId = connection.device.value?.id
        viewModelScope.launch {
            val layout = repository.layoutFor(deviceId).first()
            _state.value = EditorState(EditHistory(layout), pageId = layout.pages.first().id)
            loadCatalog()
        }
    }

    private suspend fun loadCatalog() {
        val connected = connection.state.value == ConnectionState.Connected
        val apps = connection.installedApps().map { CatalogApp(it.id, it.name) }
        val items = ButtonCatalog.items(apps)
        // Without a connection the TV cannot say what it supports, so everything is offered.
        _catalog.value = if (connected) ButtonCatalog.available(items, connection.capabilities.value, connection::supports) else items
    }

    /** Applies an edit; false when it was refused (an overlap, no room) and nothing changed. */
    private fun edit(transform: EditorState.(RemoteLayout) -> RemoteLayout?): Boolean {
        val current = _state.value ?: return false
        val next = current.transform(current.layout) ?: return false
        _state.value = current.copy(history = current.history.edit(next)).settled()
        return true
    }

    /** Keeps the page and selection pointing at things that still exist. */
    private fun EditorState.settled(): EditorState {
        val page = layout.page(pageId) ?: layout.pages.first()
        return copy(pageId = page.id, selectedId = selectedId?.takeIf { id -> page.widgets.any { it.id == id } })
    }

    fun undo() {
        val current = _state.value ?: return
        _state.value = current.copy(history = current.history.undo()).settled()
    }

    fun selectPage(pageId: String) {
        _state.value = _state.value?.copy(pageId = pageId, selectedId = null)?.settled()
    }

    fun select(widgetId: String?) {
        _state.value = _state.value?.copy(selectedId = widgetId)?.settled()
    }

    fun move(
        widgetId: String,
        column: Int,
        row: Int,
    ): Boolean = edit { it.moveWidget(pageId, widgetId, column, row) }

    /** Moves a widget one cell; the non-drag way to move (EditMode accessibility). */
    fun nudge(
        widgetId: String,
        columns: Int,
        rows: Int,
    ): Boolean {
        val widget =
            _state.value
                ?.page
                ?.widgets
                ?.firstOrNull { it.id == widgetId } ?: return false
        return move(widgetId, widget.column + columns, widget.row + rows)
    }

    fun resize(
        widgetId: String,
        span: LayoutEditing.Span,
    ): Boolean = edit { it.resizeWidget(pageId, widgetId, span) }

    fun update(
        widgetId: String,
        transform: (LayoutWidget) -> LayoutWidget,
    ): Boolean = edit { it.updateWidget(pageId, widgetId, transform) }

    fun remove(widgetId: String) {
        edit { it.removeWidget(pageId, widgetId) }
    }

    /** Places a catalog item in the first free cell of the current page (ButtonCatalog). */
    fun add(item: CatalogItem): Boolean {
        val added = edit { it.addWidget(pageId, item.widget) }
        if (!added) _messages.tryEmit("No room for ${item.title} on this page")
        return added
    }

    fun addPage(name: String) {
        val before =
            _state.value
                ?.layout
                ?.pages
                ?.map { it.id }
                .orEmpty()
                .toSet()
        edit { it.addPage(name) }
        val created =
            _state.value
                ?.layout
                ?.pages
                ?.firstOrNull { it.id !in before } ?: return
        selectPage(created.id)
    }

    fun renamePage(
        pageId: String,
        name: String,
    ) {
        edit { it.renamePage(pageId, name) }
    }

    /** Moves a page [by] places left (negative) or right. */
    fun movePage(
        pageId: String,
        by: Int,
    ) {
        edit { layout ->
            val index = layout.pages.indexOfFirst { it.id == pageId }
            layout.movePage(pageId, index + by)
        }
    }

    fun deletePage(pageId: String) {
        edit { it.deletePage(pageId) }
    }

    /** Puts the default layout back (FR-8.6). Undo brings the edited one back; Done saves it. */
    fun resetToDefault() {
        if (edit { DefaultLayouts.standard }) _messages.tryEmit("Default layout restored. Tap Done to keep it.")
    }

    /** Takes the layout of another saved TV (FR-8.7). */
    fun copyFrom(tv: TvDevice) {
        viewModelScope.launch {
            val layout = repository.layoutFor(tv.id).first()
            if (edit { layout }) _messages.emit("Copied the layout from ${tv.name}. Tap Done to keep it.")
        }
    }

    /** Takes a layout from a file (FR-12.2). */
    fun import(text: String) {
        val imported =
            try {
                LayoutFile.decode(text)
            } catch (e: LayoutFileException) {
                _messages.tryEmit(e.message ?: "This file cannot be imported")
                return
            }
        edit { imported.layout }
        _messages.tryEmit(imported.summary() + " Tap Done to keep it.")
    }

    fun report(message: String) {
        _messages.tryEmit(message)
    }

    /** Saves the edited layout for the current TV, then calls [onSaved]. */
    fun save(onSaved: () -> Unit) {
        val layout = _state.value?.layout ?: return onSaved()
        viewModelScope.launch {
            deviceId?.let { repository.saveLayout(it, layout) }
            onSaved()
        }
    }

    companion object {
        fun factory(container: AppContainer) =
            viewModelFactory { initializer { LayoutEditorViewModel(container.repository, container.connection) } }
    }
}
