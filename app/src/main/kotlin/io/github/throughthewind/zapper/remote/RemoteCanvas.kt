package io.github.throughthewind.zapper.remote

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.DPadWidget
import io.github.throughthewind.zapper.core.model.LayoutButton
import io.github.throughthewind.zapper.core.model.LayoutWidget
import io.github.throughthewind.zapper.core.model.MediaBarWidget
import io.github.throughthewind.zapper.core.model.NumberPadWidget
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.model.RemotePage
import io.github.throughthewind.zapper.core.model.RockerKind
import io.github.throughthewind.zapper.core.model.RockerWidget
import io.github.throughthewind.zapper.core.designsystem.components.RemoteKey as KeyButton

/** What the canvas needs from the connection: send a key, run a button, and whether a key works. */
interface RemoteActions {
    fun send(key: RemoteKey)

    fun press(action: ButtonAction)

    fun isEnabled(key: RemoteKey): Boolean
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
private fun PageDots(
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
    BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val cell = ((maxWidth - RemoteGrid.gutter * (columns - 1)) / columns).coerceIn(RemoteGrid.minCell, RemoteGrid.maxCell)
        val gridWidth = RemoteGrid.spanSize(cell, columns)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(width = gridWidth, height = RemoteGrid.spanSize(cell, page.rows.coerceAtLeast(1))),
        ) {
            page.widgets.forEach { widget ->
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

private fun position(
    cell: Dp,
    index: Int,
): Dp = (cell + RemoteGrid.gutter) * index

@Composable
private fun Widget(
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
    }
}

@Composable
private fun ButtonWidget(
    button: LayoutButton,
    actions: RemoteActions,
) {
    val key = (button.action as? ButtonAction.Key)?.key
    val title = button.label ?: key?.title() ?: button.action.fallbackTitle()
    KeyButton(
        label = title,
        onPress = { actions.press(button.action) },
        emphasis = key.emphasis(),
        enabled = key?.let(actions::isEnabled) ?: true,
        modifier = Modifier.fillMaxSize(),
    ) {
        val icon = key?.icon()
        if (icon != null) {
            Icon(icon, contentDescription = null)
        } else {
            Text(title.uppercase(), style = KeyType.label)
        }
    }
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

private fun RemoteKey?.emphasis(): KeyEmphasis =
    when (this) {
        RemoteKey.POWER -> KeyEmphasis.Power
        RemoteKey.OK -> KeyEmphasis.Primary
        else -> KeyEmphasis.Standard
    }

private fun RemoteKey.icon(): ImageVector? =
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
private fun RemoteKey.title(): String =
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
        else -> name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

private fun ButtonAction.fallbackTitle(): String =
    when (this) {
        is ButtonAction.Key -> key.name
        is ButtonAction.LaunchApp -> appId
        is ButtonAction.Macro -> keys.joinToString(" ")
    }
