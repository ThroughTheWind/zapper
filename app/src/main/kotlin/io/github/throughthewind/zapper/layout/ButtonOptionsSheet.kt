package io.github.throughthewind.zapper.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.designsystem.components.colors
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.KeyColor
import io.github.throughthewind.zapper.core.model.KeyIcons
import io.github.throughthewind.zapper.core.model.LabelWidget
import io.github.throughthewind.zapper.core.model.LayoutButton
import io.github.throughthewind.zapper.core.model.LayoutEditing
import io.github.throughthewind.zapper.core.model.LayoutEditing.span
import io.github.throughthewind.zapper.core.model.LayoutWidget
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.canRepeat
import io.github.throughthewind.zapper.remote.emphasis
import io.github.throughthewind.zapper.remote.keyIcon
import io.github.throughthewind.zapper.remote.title

/** What the options sheet can change, each one an undoable edit. */
interface ButtonOptionsActions {
    fun update(transform: (LayoutWidget) -> LayoutWidget): Boolean

    fun resize(span: LayoutEditing.Span): Boolean

    fun nudge(
        columns: Int,
        rows: Int,
    ): Boolean

    fun remove()
}

/**
 * The options of a tapped widget (docs/design/components/ButtonConfig, SPEC FR-8.4): label, icon,
 * colour and repeat for buttons; size and move for every widget, so nothing needs a drag
 * (EditMode accessibility). Changes apply live; the label applies when the sheet closes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ButtonOptionsSheet(
    widget: LayoutWidget,
    actions: ButtonOptionsActions,
    onDismiss: () -> Unit,
) {
    val button = widget as? LayoutButton
    val label = widget as? LabelWidget
    var text by rememberSaveable(widget.id) { mutableStateOf(button?.label ?: label?.text ?: "") }
    val maxLength = if (button != null && widget.columnSpan == 1) MAX_LABEL_1X1 else MAX_LABEL
    val close = {
        applyText(widget, text, actions)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WidgetPreview(widget, Modifier.size(56.dp))
                Column {
                    Text(widgetTitle(widget), style = MaterialTheme.typography.titleMedium)
                    Text(
                        describe(widget),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (button != null || label != null) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(maxLength) },
                    label = { Text(if (label != null) "Text" else "Label (optional)") },
                    supportingText = { Text("${text.length} / $maxLength") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (button != null && button.action !is ButtonAction.LaunchApp) {
                Heading("Icon")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconChoice(name = null, selected = button.icon == null) { pick(actions, null) }
                    KeyIcons.names.forEach { name ->
                        IconChoice(name = name, selected = button.icon == name) { pick(actions, name) }
                    }
                }
            }

            if (button != null) {
                Heading("Colour")
                val chosen = button.color ?: defaultColor(button)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colorChoices(button).forEach { color ->
                        ColorOption(color, selected = color == chosen) {
                            actions.update { (it as LayoutButton).copy(color = color.takeUnless { c -> c == defaultColor(button) }) }
                        }
                    }
                }
                if (button.action.canRepeat) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.fillMaxWidth().toggleable(value = button.repeats, role = Role.Switch) { on ->
                                actions.update { (it as LayoutButton).copy(repeat = on) }
                            },
                    ) {
                        Text("Repeat while held", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = button.repeats, onCheckedChange = null)
                    }
                }
            }

            val spans = LayoutEditing.allowedSpans(widget)
            if (spans.size > 1) {
                Heading("Size")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    spans.forEach { span ->
                        FilterChip(
                            selected = span == widget.span,
                            onClick = { actions.resize(span) },
                            label = { Text("$span") },
                        )
                    }
                }
            }

            Heading("Move")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoveButton(Icons.AutoMirrored.Rounded.ArrowBack, "Move left") { actions.nudge(-1, 0) }
                MoveButton(Icons.Rounded.ArrowUpward, "Move up") { actions.nudge(0, -1) }
                MoveButton(Icons.Rounded.ArrowDownward, "Move down") { actions.nudge(0, 1) }
                MoveButton(Icons.AutoMirrored.Rounded.ArrowForward, "Move right") { actions.nudge(1, 0) }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                TextButton(onClick = {
                    actions.remove()
                    onDismiss()
                }) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = close) { Text("Done") }
            }
        }
    }
}

private fun applyText(
    widget: LayoutWidget,
    text: String,
    actions: ButtonOptionsActions,
) {
    val trimmed = text.trim()
    when (widget) {
        is LayoutButton -> {
            if (trimmed !=
                (widget.label ?: "")
            ) {
                actions.update { (it as LayoutButton).copy(label = trimmed.ifEmpty { null }) }
            }
        }

        is LabelWidget -> {
            if (trimmed.isNotEmpty() && trimmed != widget.text) actions.update { (it as LabelWidget).copy(text = trimmed) }
        }

        else -> {
            Unit
        }
    }
}

private fun pick(
    actions: ButtonOptionsActions,
    icon: String?,
) {
    actions.update { (it as LayoutButton).copy(icon = icon) }
}

/** What the widget does, under its name. */
private fun describe(widget: LayoutWidget): String =
    when (widget) {
        is LayoutButton -> {
            when (val action = widget.action) {
                is ButtonAction.Key -> "Sends ${action.key.title()}"
                is ButtonAction.LaunchApp -> "Opens ${widget.title()} on the TV"
                is ButtonAction.Macro -> "Sends ${action.keys.size} keys in a row"
                ButtonAction.OpenKeyboard -> "Types on the TV with the phone keyboard"
            }
        }

        is LabelWidget -> {
            "A caption for the keys below it"
        }

        else -> {
            "${widget.columnSpan}x${widget.rowSpan}"
        }
    }

/** The colour a button has when the user did not pick one. */
private fun defaultColor(button: LayoutButton): KeyColor {
    val action = button.action
    return when {
        action is ButtonAction.LaunchApp -> KeyColor.TERTIARY
        action == ButtonAction.Key(RemoteKey.POWER) -> KeyColor.POWER
        action == ButtonAction.Key(RemoteKey.OK) -> KeyColor.PRIMARY
        else -> KeyColor.STANDARD
    }
}

/** Theme roles only, so every theme stays legible (theming-and-customisation); Power keeps its own family. */
private fun colorChoices(button: LayoutButton): List<KeyColor> =
    when {
        button.action is ButtonAction.LaunchApp -> listOf(KeyColor.PRIMARY, KeyColor.SECONDARY, KeyColor.TERTIARY)
        button.action == ButtonAction.Key(RemoteKey.POWER) -> listOf(KeyColor.POWER, KeyColor.STANDARD)
        else -> listOf(KeyColor.STANDARD, KeyColor.PRIMARY, KeyColor.SECONDARY, KeyColor.TERTIARY, KeyColor.GHOST)
    }

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ColorOption(
    color: KeyColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fill = color.emphasis().colors()
    val ring = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(48.dp)
            .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier)
            .padding(5.dp)
            .clip(CircleShape)
            .background(fill.container)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = color.name.lowercase().replaceFirstChar { it.uppercase() } },
    )
}

@Composable
private fun IconChoice(
    name: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHigh)
            .then(if (selected) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name?.let { "$it icon" } ?: "Default icon" },
        contentAlignment = Alignment.Center,
    ) {
        val icon = name?.let(::keyIcon)
        if (icon != null) {
            Icon(icon, contentDescription = null)
        } else {
            Text("Auto", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MoveButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Icon(icon, contentDescription = null)
    }
}

/** ButtonConfig: six characters on a 1x1 key, so the label fits. */
private const val MAX_LABEL_1X1 = 6
private const val MAX_LABEL = 16
