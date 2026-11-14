package io.github.throughthewind.zapper.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.designsystem.components.AppShortcut
import io.github.throughthewind.zapper.core.designsystem.components.AppShortcutColor
import io.github.throughthewind.zapper.core.designsystem.components.DPad
import io.github.throughthewind.zapper.core.designsystem.components.DPadDirection
import io.github.throughthewind.zapper.core.designsystem.components.KeyEmphasis
import io.github.throughthewind.zapper.core.designsystem.components.MediaControl
import io.github.throughthewind.zapper.core.designsystem.components.MediaControls
import io.github.throughthewind.zapper.core.designsystem.components.NumberPad
import io.github.throughthewind.zapper.core.designsystem.components.NumberPadKey
import io.github.throughthewind.zapper.core.designsystem.components.Rocker
import io.github.throughthewind.zapper.core.designsystem.components.RockerStyle
import io.github.throughthewind.zapper.core.designsystem.theme.KeyType
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteGrid
import io.github.throughthewind.zapper.core.designsystem.theme.keyShape
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.DPadWidget
import io.github.throughthewind.zapper.core.model.KeyColor
import io.github.throughthewind.zapper.core.model.KnownApps
import io.github.throughthewind.zapper.core.model.LabelWidget
import io.github.throughthewind.zapper.core.model.LayoutButton
import io.github.throughthewind.zapper.core.model.LayoutWidget
import io.github.throughthewind.zapper.core.model.MediaBarWidget
import io.github.throughthewind.zapper.core.model.NumberPadWidget
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.model.RemotePage
import io.github.throughthewind.zapper.core.model.RockerKind
import io.github.throughthewind.zapper.core.model.RockerWidget
import io.github.throughthewind.zapper.core.model.SpacerWidget
import io.github.throughthewind.zapper.core.model.UnsupportedWidget
import io.github.throughthewind.zapper.core.designsystem.components.RemoteKey as KeyButton

/** What the canvas needs from the connection: send a key, run a button, and whether either works. */
interface RemoteActions {
    fun send(key: RemoteKey)

    fun press(action: ButtonAction)

    fun isEnabled(key: RemoteKey): Boolean

    /** Whether a button's action works on this TV right now. */
    fun isAvailable(action: ButtonAction): Boolean

    /** A placeholder for a button this version cannot run was tapped (LayoutExtras). */
    fun notSupported() = Unit
}

/** Actions for drawing widgets that do nothing, as in edit mode and the catalog. */
object PreviewActions : RemoteActions {
    override fun send(key: RemoteKey) = Unit

    override fun press(action: ButtonAction) = Unit

    override fun isEnabled(key: RemoteKey) = true

    override fun isAvailable(action: ButtonAction) = true
}

/**
 * The remote's pages, swiped horizontally with dots below (docs/design/components/RemotePages).
 * Each page is laid out on the design system's grid: 4 columns, cells between 64 and 96 dp.
 */
@Composable
fun RemoteCanvas(
    layout: RemoteLayout,
    actions: RemoteActions,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState { layout.pages.size }
    Column(modifier) {
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth(), key = { layout.pages[it].id }) { index ->
            RemotePageGrid(layout.pages[index], layout.columns, actions)
        }
        if (layout.pages.size > 1) {
            PageDots(count = layout.pages.size, current = pager.currentPage, names = layout.pages.map { it.name })
        }
    }
}

@Composable
internal fun PageDots(
    count: Int,
    current: Int,
    names: List<String>,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .semantics { contentDescription = "Page ${current + 1} of $count, ${names[current]}" },
    ) {
        repeat(count) { index ->
            val color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun RemotePageGrid(
    page: RemotePage,
    columns: Int,
    actions: RemoteActions,
) {
    val rows = page.rows.coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Square cells that fit both the width and the height left under the header, so the page
        // never scrolls. Only a screen too short for 56 dp keys falls back to scrolling.
        val byWidth = (maxWidth - RemoteGrid.gutter * (columns - 1)) / columns
        val byHeight = (maxHeight - RemoteGrid.gutter * (rows - 1)) / rows
        val cell = minOf(byWidth, byHeight, RemoteGrid.maxCell).coerceAtLeast(RemoteGrid.minTouchTarget)
        val scrolls = byHeight < RemoteGrid.minTouchTarget
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .size(width = RemoteGrid.spanSize(cell, columns), height = RemoteGrid.spanSize(cell, rows)),
        ) {
            page.widgets.forEach { widget ->
                key(widget.id) {
                    Box(
                        Modifier
                            .offset(x = position(cell, widget.column), y = position(cell, widget.row))
                            .size(RemoteGrid.spanSize(cell, widget.columnSpan), RemoteGrid.spanSize(cell, widget.rowSpan)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Widget(widget, actions)
                    }
                }
            }
        }
    }
}

internal fun position(
    cell: Dp,
    index: Int,
): Dp = (cell + RemoteGrid.gutter) * index

/** A widget as the remote draws it; edit mode draws the same thing with [PreviewActions]. */
@Composable
internal fun Widget(
    widget: LayoutWidget,
    actions: RemoteActions,
) {
    when (widget) {
        is LayoutButton -> {
            ButtonWidget(widget, actions)
        }

        is DPadWidget -> {
            DPad(
                onPress = { actions.send(it.key()) },
                enabled = actions.isEnabled(RemoteKey.OK),
                modifier = Modifier.fillMaxSize(),
            )
        }

        is RockerWidget -> {
            Rocker(
                style = if (widget.kind == RockerKind.VOLUME) RockerStyle.Volume else RockerStyle.Channel,
                onUp = { actions.send(widget.kind.up) },
                onDown = { actions.send(widget.kind.down) },
                enabled = actions.isEnabled(widget.kind.up),
                modifier = Modifier.fillMaxSize(),
            )
        }

        is NumberPadWidget -> {
            NumberPad(
                onPress = { actions.send(it.key()) },
                enabled = { actions.isEnabled(it.key()) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is MediaBarWidget -> {
            MediaControls(
                onPress = { actions.send(it.key()) },
                compact = widget.columnSpan == 3,
                enabled = { actions.isEnabled(it.key()) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is SpacerWidget -> {
            Unit
        }

        is LabelWidget -> {
            SectionLabel(widget.text, Modifier.fillMaxSize())
        }

        is UnsupportedWidget -> {
            UnsupportedKey(onPress = actions::notSupported, modifier = Modifier.fillMaxSize())
        }
    }
}

/** A caption grouping keys, with a hairline under it (LayoutExtras: Label). */
@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        Text(
            text.uppercase(),
            style = KeyType.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * A button this version cannot run, kept from an imported layout (LayoutExtras: Unsupported
 * placeholder): hatched and outlined, with a help badge. Tapping it says why.
 */
@Composable
private fun UnsupportedKey(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    val hatch = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .clip(keyShape)
            .border(1.dp, outline, keyShape)
            .drawBehind {
                val step = 8.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(hatch, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                    x += step
                }
            }.clickable(onClickLabel = "Why", onClick = onPress)
            .semantics { contentDescription = "Not supported on this TV" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ButtonWidget(
    button: LayoutButton,
    actions: RemoteActions,
) {
    val action = button.action
    if (action is ButtonAction.LaunchApp) {
        AppShortcut(
            name = button.title(),
            onPress = { actions.press(action) },
            columns = button.columnSpan,
            rows = button.rowSpan,
            color =
                when (button.color) {
                    KeyColor.PRIMARY -> AppShortcutColor.Primary
                    KeyColor.SECONDARY -> AppShortcutColor.Secondary
                    else -> AppShortcutColor.Tertiary
                },
            enabled = actions.isAvailable(action),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val key = (action as? ButtonAction.Key)?.key
    val title = button.title()
    KeyButton(
        label = title,
        onPress = { actions.press(button.action) },
        emphasis = button.color?.emphasis() ?: key.emphasis(),
        enabled = actions.isAvailable(button.action),
        repeatOnHold = button.repeats,
        modifier = Modifier.fillMaxSize(),
    ) {
        val icon = button.icon?.let(::keyIcon) ?: key?.icon() ?: button.action.icon()
        val label = button.label
        when {
            icon != null && label != null && button.columnSpan > 1 -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, contentDescription = null)
                    Text(label.uppercase(), style = KeyType.label, maxLines = 1)
                }
            }

            icon != null && label == null -> {
                Icon(icon, contentDescription = null)
            }

            else -> {
                Text(title.uppercase(), style = KeyType.label, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

/** The name a button shows and is announced by: its label, else its key or app name. */
internal fun LayoutButton.title(): String {
    label?.let { return it }
    return when (val a = action) {
        is ButtonAction.Key -> a.key.title()
        is ButtonAction.LaunchApp -> KnownApps.named(a.appId)?.name ?: a.appId
        else -> a.fallbackTitle()
    }
}

internal fun KeyColor.emphasis(): KeyEmphasis =
    when (this) {
        KeyColor.STANDARD -> KeyEmphasis.Standard
        KeyColor.PRIMARY -> KeyEmphasis.Primary
        KeyColor.SECONDARY -> KeyEmphasis.Secondary
        KeyColor.TERTIARY -> KeyEmphasis.Tertiary
        KeyColor.GHOST -> KeyEmphasis.Ghost
        KeyColor.POWER -> KeyEmphasis.Power
    }

/** The icon a user picked in the button options, by its [io.github.throughthewind.zapper.core.model.KeyIcons] name. */
internal fun keyIcon(name: String): ImageVector? =
    when (name) {
        "power" -> Icons.Rounded.PowerSettingsNew
        "home" -> Icons.Rounded.Home
        "back" -> Icons.AutoMirrored.Rounded.ArrowBack
        "exit" -> Icons.AutoMirrored.Rounded.ExitToApp
        "menu" -> Icons.Rounded.Menu
        "source" -> Icons.AutoMirrored.Rounded.Input
        "settings" -> Icons.Rounded.Settings
        "mute" -> Icons.AutoMirrored.Rounded.VolumeOff
        "info" -> Icons.Rounded.Info
        "keyboard" -> Icons.Rounded.Keyboard
        "guide" -> Icons.Rounded.GridView
        "list" -> Icons.AutoMirrored.Rounded.ViewList
        "search" -> Icons.Rounded.Search
        "star" -> Icons.Rounded.Star
        "favorite" -> Icons.Rounded.Favorite
        "tv" -> Icons.Rounded.Tv
        "movie" -> Icons.Rounded.Movie
        "music" -> Icons.Rounded.MusicNote
        "sports" -> Icons.Rounded.SportsSoccer
        "subtitles" -> Icons.Rounded.Subtitles
        "replay" -> Icons.Rounded.Replay
        "stop" -> Icons.Rounded.Stop
        else -> null
    }

private fun DPadDirection.key(): RemoteKey =
    when (this) {
        DPadDirection.Up -> RemoteKey.UP
        DPadDirection.Down -> RemoteKey.DOWN
        DPadDirection.Left -> RemoteKey.LEFT
        DPadDirection.Right -> RemoteKey.RIGHT
        DPadDirection.Ok -> RemoteKey.OK
    }

private fun NumberPadKey.key(): RemoteKey =
    when (this) {
        is NumberPadKey.Digit -> RemoteKey.valueOf("DIGIT_$value")
        NumberPadKey.ChannelList -> RemoteKey.CHANNEL_LIST
        NumberPadKey.PreviousChannel -> RemoteKey.PREVIOUS_CHANNEL
    }

private fun MediaControl.key(): RemoteKey =
    when (this) {
        MediaControl.Rewind -> RemoteKey.REWIND
        MediaControl.PlayPause -> RemoteKey.PLAY_PAUSE
        MediaControl.FastForward -> RemoteKey.FAST_FORWARD
    }

internal fun RemoteKey?.emphasis(): KeyEmphasis =
    when (this) {
        RemoteKey.POWER -> KeyEmphasis.Power
        RemoteKey.OK -> KeyEmphasis.Primary
        RemoteKey.SOURCE, RemoteKey.HDMI_1, RemoteKey.HDMI_2, RemoteKey.HDMI_3, RemoteKey.HDMI_4 -> KeyEmphasis.Tertiary
        else -> KeyEmphasis.Standard
    }

internal fun RemoteKey.icon(): ImageVector? =
    when (this) {
        RemoteKey.POWER -> Icons.Rounded.PowerSettingsNew
        RemoteKey.HOME -> Icons.Rounded.Home
        RemoteKey.BACK -> Icons.AutoMirrored.Rounded.ArrowBack
        RemoteKey.EXIT -> Icons.AutoMirrored.Rounded.ExitToApp
        RemoteKey.MENU -> Icons.Rounded.Menu
        RemoteKey.SOURCE -> Icons.AutoMirrored.Rounded.Input
        RemoteKey.SETTINGS -> Icons.Rounded.Settings
        RemoteKey.MUTE -> Icons.AutoMirrored.Rounded.VolumeOff
        RemoteKey.INFO -> Icons.Rounded.Info
        else -> null
    }

/** The accessibility label and text of a key with no icon. */
internal fun RemoteKey.title(): String =
    when (this) {
        RemoteKey.POWER -> "Power"
        RemoteKey.HOME -> "Home"
        RemoteKey.BACK -> "Back"
        RemoteKey.EXIT -> "Exit"
        RemoteKey.MENU -> "Menu"
        RemoteKey.SOURCE -> "Source"
        RemoteKey.SETTINGS -> "Settings"
        RemoteKey.MUTE -> "Mute"
        RemoteKey.INFO -> "Info"
        RemoteKey.GUIDE -> "Guide"
        RemoteKey.HDMI_1 -> "HDMI 1"
        RemoteKey.HDMI_2 -> "HDMI 2"
        RemoteKey.HDMI_3 -> "HDMI 3"
        RemoteKey.HDMI_4 -> "HDMI 4"
        else -> name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

private fun ButtonAction.fallbackTitle(): String =
    when (this) {
        is ButtonAction.Key -> key.name
        is ButtonAction.LaunchApp -> appId
        is ButtonAction.Macro -> keys.joinToString(" ")
        ButtonAction.OpenKeyboard -> "Keyboard"
    }

internal fun ButtonAction.icon(): ImageVector? =
    when (this) {
        ButtonAction.OpenKeyboard -> Icons.Rounded.Keyboard
        else -> null
    }
