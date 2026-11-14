package io.github.throughthewind.zapper.layout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteGrid
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteHaptic
import io.github.throughthewind.zapper.core.designsystem.theme.rememberRemoteHaptics
import io.github.throughthewind.zapper.core.model.LayoutEditing
import io.github.throughthewind.zapper.core.model.LayoutEditing.movedTo
import io.github.throughthewind.zapper.core.model.LayoutEditing.resizedTo
import io.github.throughthewind.zapper.core.model.LayoutWidget
import io.github.throughthewind.zapper.core.model.RemotePage
import io.github.throughthewind.zapper.remote.PreviewActions
import io.github.throughthewind.zapper.remote.Widget
import io.github.throughthewind.zapper.remote.position
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the canvas asks of edit mode. Moves and resizes return false when refused. */
interface EditCanvasActions {
    fun select(widgetId: String)

    fun move(
        widgetId: String,
        column: Int,
        row: Int,
    ): Boolean

    fun nudge(
        widgetId: String,
        columns: Int,
        rows: Int,
    ): Boolean

    fun resize(
        widgetId: String,
        span: LayoutEditing.Span,
    ): Boolean

    fun remove(widgetId: String)
}

/** A widget being dragged: how far it moved, in pixels, and whether the finger is over the trash zone. */
private data class Drag(
    val widgetId: String,
    val offset: Offset,
    val overTrash: Boolean = false,
)

/** A resize in progress: how far the handle moved, in pixels. */
private data class Resize(
    val widgetId: String,
    val offset: Offset,
)

/**
 * The page in edit mode (docs/design/components/EditMode): grid dots, every widget outlined with
 * a remove badge, keys that send nothing. Drag to move, snapping cell by cell, with the drop
 * target tinted when free and red when taken; the handle of the selected widget resizes between
 * its allowed spans; dropping on [trashBounds] removes. Overlaps are refused, never reflowed.
 */
@Composable
fun EditCanvas(
    page: RemotePage,
    columns: Int,
    selectedId: String?,
    trashBounds: Rect,
    actions: EditCanvasActions,
    onDragging: (dragging: Boolean, overTrash: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberRemoteHaptics()
    val density = LocalDensity.current
    var drag by remember { mutableStateOf<Drag?>(null) }
    var resize by remember { mutableStateOf<Resize?>(null) }
    val colors = MaterialTheme.colorScheme
    // One spare row under the lowest widget, so there is always somewhere to drop.
    val rows = (page.rows + 1).coerceIn(MIN_ROWS, LayoutEditing.MAX_ROWS)

    BoxWithConstraints(modifier) {
        val byWidth = (maxWidth - RemoteGrid.gutter * (columns - 1)) / columns
        val byHeight = (maxHeight - RemoteGrid.gutter * (rows - 1)) / rows
        val cell = minOf(byWidth, byHeight, RemoteGrid.maxCell).coerceAtLeast(MIN_CELL)
        val step = with(density) { (cell + RemoteGrid.gutter).toPx() }

        // Where the dragged or resized widget would land, and whether it may.
        val target: Pair<LayoutWidget, Boolean>? =
            drag?.let { d ->
                val widget = page.widgets.firstOrNull { it.id == d.widgetId } ?: return@let null
                if (d.overTrash) return@let null
                val moved = widget.movedTo(widget.column + (d.offset.x / step).roundToInt(), widget.row + (d.offset.y / step).roundToInt())
                moved to LayoutEditing.fits(page, moved, columns)
            } ?: resize?.let { r ->
                val widget = page.widgets.firstOrNull { it.id == r.widgetId } ?: return@let null
                val span = nearestSpan(widget, r.offset, step)
                val resized = widget.resizedTo(span) ?: return@let null
                resized to LayoutEditing.fits(page, resized, columns)
            }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(RemoteGrid.spanSize(cell, columns), RemoteGrid.spanSize(cell, rows)),
        ) {
            GridDots(columns, rows, cell, colors.outlineVariant)
            target?.let { (widget, free) -> DropTarget(widget, cell, free) }
            page.widgets.forEach { widget ->
                key(widget.id) {
                    val dragOffset = drag?.takeIf { it.widgetId == widget.id }?.offset
                    EditableWidget(
                        widget = widget,
                        cell = cell,
                        selected = widget.id == selectedId,
                        dragOffset = dragOffset,
                        actions = actions,
                        onDragStart = {
                            haptics.perform(RemoteHaptic.DragStart)
                            drag = Drag(widget.id, Offset.Zero)
                            onDragging(true, false)
                        },
                        onDrag = { delta, finger ->
                            val before = drag ?: return@EditableWidget
                            val offset = before.offset + delta
                            val overTrash = trashBounds.contains(finger)
                            val oldCell = (before.offset.x / step).roundToInt() to (before.offset.y / step).roundToInt()
                            val newCell = (offset.x / step).roundToInt() to (offset.y / step).roundToInt()
                            if (overTrash != before.overTrash || (!overTrash && oldCell != newCell)) haptics.perform(RemoteHaptic.DragSnap)
                            drag = before.copy(offset = offset, overTrash = overTrash)
                            if (overTrash != before.overTrash) onDragging(true, overTrash)
                        },
                        onDragEnd = { cancelled ->
                            val done = drag
                            drag = null
                            onDragging(false, false)
                            if (done == null || cancelled) return@EditableWidget
                            haptics.perform(RemoteHaptic.DragDrop)
                            if (done.overTrash) {
                                actions.remove(widget.id)
                            } else {
                                val column = widget.column + (done.offset.x / step).roundToInt()
                                val row = widget.row + (done.offset.y / step).roundToInt()
                                if (column != widget.column || row != widget.row) actions.move(widget.id, column, row)
                            }
                        },
                        onResize = { delta -> resize = Resize(widget.id, (resize?.offset ?: Offset.Zero) + delta) },
                        onResizeEnd = { cancelled ->
                            val done = resize
                            resize = null
                            if (done != null && !cancelled) {
                                val span = nearestSpan(widget, done.offset, step)
                                if (span != with(LayoutEditing) { widget.span }) {
                                    haptics.perform(RemoteHaptic.DragDrop)
                                    actions.resize(widget.id, span)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** The allowed span closest to the widget's size plus the handle's travel. */
private fun nearestSpan(
    widget: LayoutWidget,
    offset: Offset,
    step: Float,
): LayoutEditing.Span {
    val columns = widget.columnSpan + (offset.x / step).roundToInt()
    val rows = widget.rowSpan + (offset.y / step).roundToInt()
    return LayoutEditing.allowedSpans(widget).minBy { abs(it.columns - columns) + abs(it.rows - rows) }
}

@Composable
private fun GridDots(
    columns: Int,
    rows: Int,
    cell: Dp,
    color: Color,
) {
    Canvas(Modifier.size(RemoteGrid.spanSize(cell, columns), RemoteGrid.spanSize(cell, rows))) {
        val cellPx = cell.toPx()
        val step = cellPx + RemoteGrid.gutter.toPx()
        for (c in 0 until columns) {
            for (r in 0 until rows) {
                drawCircle(color, radius = 2.dp.toPx(), center = Offset(c * step + cellPx / 2, r * step + cellPx / 2))
            }
        }
    }
}

/** Where a dragged widget would land: `primary-container` if free, `error-container` if taken. */
@Composable
private fun DropTarget(
    widget: LayoutWidget,
    cell: Dp,
    free: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val fill = if (free) colors.primaryContainer.copy(alpha = 0.7f) else colors.errorContainer.copy(alpha = 0.7f)
    val edge = if (free) colors.primary else colors.error
    Box(
        Modifier
            .offset(position(cell, widget.column), position(cell, widget.row))
            .size(RemoteGrid.spanSize(cell, widget.columnSpan), RemoteGrid.spanSize(cell, widget.rowSpan))
            .background(fill, RoundedCornerShape(16.dp))
            .dashedOutline(edge, 2.dp),
    )
}

@Composable
private fun EditableWidget(
    widget: LayoutWidget,
    cell: Dp,
    selected: Boolean,
    dragOffset: Offset?,
    actions: EditCanvasActions,
    onDragStart: () -> Unit,
    onDrag: (delta: Offset, finger: Offset) -> Unit,
    onDragEnd: (cancelled: Boolean) -> Unit,
    onResize: (Offset) -> Unit,
    onResizeEnd: (cancelled: Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dragging = dragOffset != null
    val current by rememberUpdatedState(widget)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val dragStart by rememberUpdatedState(onDragStart)
    val resizeBy by rememberUpdatedState(onResize)
    val resizeEnd by rememberUpdatedState(onResizeEnd)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val title = widgetTitle(widget)

    Box(
        Modifier
            .offset(position(cell, widget.column), position(cell, widget.row))
            .size(RemoteGrid.spanSize(cell, widget.columnSpan), RemoteGrid.spanSize(cell, widget.rowSpan))
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = dragOffset?.x ?: 0f
                translationY = dragOffset?.y ?: 0f
                val lift = if (dragging) 1.04f else 1f
                scaleX = lift
                scaleY = lift
                shadowElevation = if (dragging) 12.dp.toPx() else 0f
            },
    ) {
        // The widget as it looks on the remote; it sends nothing (EditMode: keys stop sending).
        Box(Modifier.matchParentSize().clearAndSetSemantics {}) { Widget(widget, PreviewActions) }
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (dragging || selected) {
                        Modifier.border(2.dp, colors.primary, RoundedCornerShape(16.dp))
                    } else {
                        Modifier.dashedOutline(colors.outline, 1.5.dp)
                    },
                ).onGloballyPositioned { coordinates = it }
                .pointerInput(Unit) { detectTapGestures(onTap = { actions.select(current.id) }) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragStart() },
                        onDragEnd = { dragEnd(false) },
                        onDragCancel = { dragEnd(true) },
                        onDrag = { change, amount ->
                            change.consume()
                            val finger = coordinates?.localToRoot(change.position) ?: Offset.Unspecified
                            drag(amount, finger)
                        },
                    )
                }.editSemantics(title, current, actions),
        )
        RemoveBadge(onRemove = { actions.remove(widget.id) }, title = title, modifier = Modifier.align(Alignment.TopStart))
        if (selected && LayoutEditing.allowedSpans(widget).size > 1) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { resizeEnd(false) },
                            onDragCancel = { resizeEnd(true) },
                            onDrag = { change, amount ->
                                change.consume()
                                resizeBy(amount)
                            },
                        )
                    }.clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(22.dp).background(colors.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.OpenInFull, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** TalkBack: the widget's name and place, tap for options, and every drag as an action (EditMode accessibility). */
private fun Modifier.editSemantics(
    title: String,
    widget: LayoutWidget,
    actions: EditCanvasActions,
): Modifier =
    clearAndSetSemantics {
        contentDescription = "$title, column ${widget.column + 1}, row ${widget.row + 1}"
        role = Role.Button
        onClick(label = "Options") {
            actions.select(widget.id)
            true
        }
        customActions =
            listOf(
                CustomAccessibilityAction("Move left") { actions.nudge(widget.id, -1, 0) },
                CustomAccessibilityAction("Move right") { actions.nudge(widget.id, 1, 0) },
                CustomAccessibilityAction("Move up") { actions.nudge(widget.id, 0, -1) },
                CustomAccessibilityAction("Move down") { actions.nudge(widget.id, 0, 1) },
                CustomAccessibilityAction("Remove") {
                    actions.remove(widget.id)
                    true
                },
            )
    }

@Composable
private fun RemoveBadge(
    onRemove: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val remove by rememberUpdatedState(onRemove)
    Box(
        modifier
            .size(32.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { remove() }) }
            .clearAndSetSemantics {
                contentDescription = "Remove $title"
                role = Role.Button
                onClick {
                    onRemove()
                    true
                }
            },
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.onErrorContainer, modifier = Modifier.size(14.dp))
        }
    }
}

/** A 1.5 dp dashed outline, as edit mode draws every widget. */
internal fun Modifier.dashedOutline(
    color: Color,
    width: Dp,
): Modifier =
    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = size.copy(width = size.width - stroke, height = size.height - stroke),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
        )
    }

/** Fewer rows than this would make the grid jump in size as rows fill. */
private const val MIN_ROWS = 8

/** Edit mode shows the whole page; cells may shrink below the remote's minimum to fit twelve rows. */
private val MIN_CELL = 32.dp
