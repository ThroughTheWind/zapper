package io.github.throughthewind.zapper.core.model

/**
 * The edits edit mode makes to a [RemoteLayout] (SPEC F8, docs/design/components/EditMode). Each
 * returns a new layout, or null when the edit is refused: widgets never overlap and never leave
 * the grid, so the layout never shifts under the user. Pure, so the rules are tested on the JVM.
 */
object LayoutEditing {
    /** The deepest a page may grow, in rows. Twelve 56 dp rows still fit a tall phone. */
    const val MAX_ROWS = 12

    /** A widget's size in grid cells. */
    data class Span(
        val columns: Int,
        val rows: Int,
    ) {
        override fun toString() = "${columns}x$rows"
    }

    /** The sizes the resize handle snaps between (EditMode, WidgetType.allowedSpans). */
    fun allowedSpans(widget: LayoutWidget): List<Span> =
        when (widget) {
            is LayoutButton -> {
                if (widget.action is ButtonAction.LaunchApp) {
                    listOf(Span(1, 1), Span(2, 1), Span(2, 2))
                } else {
                    listOf(Span(1, 1), Span(2, 1))
                }
            }

            is DPadWidget -> {
                listOf(Span(3, 3), Span(4, 4))
            }

            is RockerWidget -> {
                listOf(Span(1, 2), Span(1, 3))
            }

            is NumberPadWidget -> {
                listOf(Span(3, 4))
            }

            is MediaBarWidget -> {
                listOf(Span(3, 1), Span(4, 1))
            }

            is SpacerWidget -> {
                listOf(Span(1, 1), Span(2, 1), Span(4, 1))
            }

            is LabelWidget -> {
                listOf(Span(2, 1), Span(3, 1), Span(4, 1))
            }

            is UnsupportedWidget -> {
                listOf(Span(widget.columnSpan, widget.rowSpan))
            }
        }

    val LayoutWidget.span: Span get() = Span(columnSpan, rowSpan)

    /** The same widget at another cell. */
    fun LayoutWidget.movedTo(
        column: Int,
        row: Int,
    ): LayoutWidget =
        when (this) {
            is LayoutButton -> copy(column = column, row = row)
            is DPadWidget -> copy(column = column, row = row)
            is RockerWidget -> copy(column = column, row = row)
            is NumberPadWidget -> copy(column = column, row = row)
            is MediaBarWidget -> copy(column = column, row = row)
            is SpacerWidget -> copy(column = column, row = row)
            is LabelWidget -> copy(column = column, row = row)
            is UnsupportedWidget -> copy(column = column, row = row)
        }

    /** The same widget at [span], or null when the widget cannot take that size. */
    fun LayoutWidget.resizedTo(span: Span): LayoutWidget? {
        if (span !in allowedSpans(this)) return null
        return when (this) {
            is LayoutButton -> copy(columnSpan = span.columns, rowSpan = span.rows)
            is DPadWidget -> copy(size = span.columns)
            is RockerWidget -> copy(rowSpan = span.rows)
            is NumberPadWidget -> this
            is MediaBarWidget -> copy(columnSpan = span.columns)
            is SpacerWidget -> copy(columnSpan = span.columns)
            is LabelWidget -> copy(columnSpan = span.columns)
            is UnsupportedWidget -> this
        }
    }

    /** The same widget with another id, for copies and new catalog items. */
    fun LayoutWidget.withId(id: String): LayoutWidget =
        when (this) {
            is LayoutButton -> copy(id = id)
            is DPadWidget -> copy(id = id)
            is RockerWidget -> copy(id = id)
            is NumberPadWidget -> copy(id = id)
            is MediaBarWidget -> copy(id = id)
            is SpacerWidget -> copy(id = id)
            is LabelWidget -> copy(id = id)
            is UnsupportedWidget -> copy(id = id)
        }

    /** Every cell the widget covers, as (column, row). */
    fun LayoutWidget.cells(): List<Pair<Int, Int>> =
        (column until column + columnSpan).flatMap { c -> (row until row + rowSpan).map { r -> c to r } }

    /** Whether [widget] fits on [page]: inside the grid and on no other widget. */
    fun fits(
        page: RemotePage,
        widget: LayoutWidget,
        columns: Int,
    ): Boolean {
        if (widget.column < 0 || widget.row < 0) return false
        if (widget.column + widget.columnSpan > columns || widget.row + widget.rowSpan > MAX_ROWS) return false
        val taken =
            page.widgets
                .filter { it.id != widget.id }
                .flatMap { it.cells() }
                .toSet()
        return widget.cells().none { it in taken }
    }

    /** The first free place for [widget] on [page], reading row by row, or null when the page is full. */
    fun firstFreeSlot(
        page: RemotePage,
        widget: LayoutWidget,
        columns: Int,
    ): Pair<Int, Int>? {
        for (row in 0..MAX_ROWS - widget.rowSpan) {
            for (column in 0..columns - widget.columnSpan) {
                if (fits(page, widget.movedTo(column, row), columns)) return column to row
            }
        }
        return null
    }

    /** An id no widget of [layout] uses yet, built from [base]. */
    fun freshId(
        layout: RemoteLayout,
        base: String,
    ): String {
        val taken = layout.pages.flatMap { page -> page.widgets.map { it.id } }.toSet()
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it !in taken }
    }

    /** An id no page of [layout] uses yet. */
    fun freshPageId(layout: RemoteLayout): String {
        val taken = layout.pages.map { it.id }.toSet()
        return generateSequence(1) { it + 1 }.map { "page-$it" }.first { it !in taken }
    }

    fun RemoteLayout.page(pageId: String): RemotePage? = pages.firstOrNull { it.id == pageId }

    private fun RemoteLayout.withPage(
        pageId: String,
        transform: (RemotePage) -> RemotePage?,
    ): RemoteLayout? {
        val page = page(pageId) ?: return null
        val changed = transform(page) ?: return null
        return copy(pages = pages.map { if (it.id == pageId) changed else it })
    }

    private fun RemoteLayout.withWidget(
        pageId: String,
        widgetId: String,
        transform: (LayoutWidget) -> LayoutWidget?,
    ): RemoteLayout? =
        withPage(pageId) { page ->
            val widget = page.widgets.firstOrNull { it.id == widgetId } ?: return@withPage null
            val changed = transform(widget) ?: return@withPage null
            if (!fits(page, changed, columns)) return@withPage null
            page.copy(widgets = page.widgets.map { if (it.id == widgetId) changed else it })
        }

    /** Moves a widget so its top-left corner sits at ([column], [row]); refused onto another widget. */
    fun RemoteLayout.moveWidget(
        pageId: String,
        widgetId: String,
        column: Int,
        row: Int,
    ): RemoteLayout? = withWidget(pageId, widgetId) { it.movedTo(column, row) }

    /** Resizes a widget in place, keeping its top-left corner; refused when the new size overlaps. */
    fun RemoteLayout.resizeWidget(
        pageId: String,
        widgetId: String,
        span: Span,
    ): RemoteLayout? = withWidget(pageId, widgetId) { it.resizedTo(span) }

    /** Changes a button's options (label, icon, colour, repeat), keeping its place. */
    fun RemoteLayout.updateWidget(
        pageId: String,
        widgetId: String,
        transform: (LayoutWidget) -> LayoutWidget,
    ): RemoteLayout? = withWidget(pageId, widgetId) { transform(it).movedTo(it.column, it.row) }

    fun RemoteLayout.removeWidget(
        pageId: String,
        widgetId: String,
    ): RemoteLayout? =
        withPage(pageId) { page ->
            if (page.widgets.none { it.id == widgetId }) return@withPage null
            page.copy(widgets = page.widgets.filterNot { it.id == widgetId })
        }

    /**
     * Places [widget] on the page: where it says if that is free, otherwise in the first free
     * place. Gives it a fresh id. Null when the page has no room left.
     */
    fun RemoteLayout.addWidget(
        pageId: String,
        widget: LayoutWidget,
    ): RemoteLayout? {
        val page = page(pageId) ?: return null
        val named = widget.withId(freshId(this, widget.id))
        val placed =
            if (fits(page, named, columns)) {
                named
            } else {
                val (column, row) = firstFreeSlot(page, named, columns) ?: return null
                named.movedTo(column, row)
            }
        return withPage(pageId) { it.copy(widgets = it.widgets + placed) }
    }

    /** Adds an empty page at the end. */
    fun RemoteLayout.addPage(name: String): RemoteLayout = copy(pages = pages + RemotePage(freshPageId(this), name.trim()))

    fun RemoteLayout.renamePage(
        pageId: String,
        name: String,
    ): RemoteLayout? = if (name.isBlank()) null else withPage(pageId) { it.copy(name = name.trim()) }

    /** Moves a page to [index], shifting the others. */
    fun RemoteLayout.movePage(
        pageId: String,
        index: Int,
    ): RemoteLayout? {
        val page = page(pageId) ?: return null
        if (index !in pages.indices) return null
        val others = pages.filterNot { it.id == pageId }
        return copy(pages = others.take(index) + page + others.drop(index))
    }

    /** Deletes a page; the last page cannot go, a layout always has one. */
    fun RemoteLayout.deletePage(pageId: String): RemoteLayout? {
        if (pages.size <= 1 || page(pageId) == null) return null
        return copy(pages = pages.filterNot { it.id == pageId })
    }
}
