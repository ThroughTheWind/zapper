package io.github.throughthewind.zapper.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A user-editable remote: pages of widgets on a grid the user can add to, move, resize and relabel
 * (docs/design: 4 columns, 72dp cells at 360dp). Stored as JSON, so every field needs a default
 * that keeps old layouts readable.
 */
@Serializable
data class RemoteLayout(
    val id: String,
    val name: String,
    val columns: Int = 4,
    val pages: List<RemotePage> = emptyList(),
) {
    init {
        require(columns > 0) { "columns must be positive, was $columns" }
    }
}

/** One swipeable page of a [RemoteLayout] (docs/design/components/RemotePages). */
@Serializable
data class RemotePage(
    val id: String,
    val name: String,
    val widgets: List<LayoutWidget> = emptyList(),
) {
    /** Rows the page needs: one past the lowest widget edge. */
    val rows: Int get() = widgets.maxOfOrNull { it.row + it.rowSpan } ?: 0
}

/**
 * Something placed on a [RemotePage]. Position and size are in grid cells. The widget types are
 * the design system's (docs/design/components): one per component, so editing in Phase 3 changes
 * the same model the default remote is drawn from.
 */
@Serializable
sealed interface LayoutWidget {
    val id: String
    val column: Int
    val row: Int
    val columnSpan: Int
    val rowSpan: Int
}

/** A single key: a [RemoteKey], an app shortcut or a macro (docs/design/components/RemoteKey). */
@Serializable
@SerialName("button")
data class LayoutButton(
    override val id: String,
    val action: ButtonAction,
    override val column: Int,
    override val row: Int,
    override val columnSpan: Int = 1,
    override val rowSpan: Int = 1,
    val label: String? = null,
    /** A [KeyIcons] name; null keeps the action's own icon. */
    val icon: String? = null,
    /** A theme role, never a free colour, so every theme stays legible (theming-and-customisation). */
    val color: KeyColor? = null,
    /** Sends again while held. Null follows [ButtonAction.repeatsByDefault]. */
    val repeat: Boolean? = null,
) : LayoutWidget {
    /** Whether holding the key sends it again and again. */
    val repeats: Boolean get() = action.canRepeat && (repeat ?: action.repeatsByDefault)
}

/** The colour of a key, as a theme role (docs/design/components/ButtonConfig). Null is the action's default. */
@Serializable
enum class KeyColor { STANDARD, PRIMARY, SECONDARY, TERTIARY, GHOST, POWER }

/** The directional pad with OK in the middle. Always square: 3x3 or 4x4 (docs/design/components/DPad). */
@Serializable
@SerialName("dpad")
data class DPadWidget(
    override val id: String,
    override val column: Int,
    override val row: Int,
    val size: Int = 3,
) : LayoutWidget {
    override val columnSpan: Int get() = size
    override val rowSpan: Int get() = size

    init {
        require(size == 3 || size == 4) { "a D-pad is 3x3 or 4x4, was $size" }
    }
}

enum class RockerKind(
    val up: RemoteKey,
    val down: RemoteKey,
) {
    VOLUME(RemoteKey.VOLUME_UP, RemoteKey.VOLUME_DOWN),
    CHANNEL(RemoteKey.CHANNEL_UP, RemoteKey.CHANNEL_DOWN),
}

/** A vertical up/down pair for volume or channel, 1x3 or 1x2 (docs/design/components/Rocker). */
@Serializable
@SerialName("rocker")
data class RockerWidget(
    override val id: String,
    val kind: RockerKind,
    override val column: Int,
    override val row: Int,
    override val rowSpan: Int = 3,
) : LayoutWidget {
    override val columnSpan: Int get() = 1

    init {
        require(rowSpan == 2 || rowSpan == 3) { "a rocker is 1x2 or 1x3, was 1x$rowSpan" }
    }
}

/** Digits 0-9 with channel list and previous channel, fixed 3x4 (docs/design/components/NumberPad). */
@Serializable
@SerialName("numberpad")
data class NumberPadWidget(
    override val id: String,
    override val column: Int,
    override val row: Int,
) : LayoutWidget {
    override val columnSpan: Int get() = 3
    override val rowSpan: Int get() = 4
}

/** Rewind, play/pause and fast-forward in one row, 4x1 or 3x1 (docs/design/components/MediaControls). */
@Serializable
@SerialName("media")
data class MediaBarWidget(
    override val id: String,
    override val column: Int,
    override val row: Int,
    override val columnSpan: Int = 4,
) : LayoutWidget {
    override val rowSpan: Int get() = 1

    init {
        require(columnSpan == 3 || columnSpan == 4) { "a media bar is 4x1 or 3x1, was ${columnSpan}x1" }
    }
}

/** Empty room that keeps keys apart: invisible on the remote, outlined in edit mode (LayoutExtras). */
@Serializable
@SerialName("spacer")
data class SpacerWidget(
    override val id: String,
    override val column: Int,
    override val row: Int,
    override val columnSpan: Int = 1,
) : LayoutWidget {
    override val rowSpan: Int get() = 1

    init {
        require(columnSpan in 1..4) { "a spacer is 1x1 to 4x1, was ${columnSpan}x1" }
    }
}

/** A caption that groups keys, such as "Streaming" (docs/design/components/LayoutExtras). */
@Serializable
@SerialName("label")
data class LabelWidget(
    override val id: String,
    val text: String,
    override val column: Int,
    override val row: Int,
    override val columnSpan: Int = 4,
) : LayoutWidget {
    override val rowSpan: Int get() = 1

    init {
        require(columnSpan in 1..4) { "a label is 1x1 to 4x1, was ${columnSpan}x1" }
    }
}

/**
 * A widget this version cannot run, kept rather than dropped (SPEC FR-12.2): an imported button
 * with an unknown type or key. Shown as a disabled placeholder (LayoutExtras) and written back
 * unchanged on export, so a newer Zapper can read it again.
 */
@Serializable
@SerialName("unsupported")
data class UnsupportedWidget(
    override val id: String,
    override val column: Int,
    override val row: Int,
    override val columnSpan: Int = 1,
    override val rowSpan: Int = 1,
    /** The widget as it was written. */
    val original: JsonObject = JsonObject(emptyMap()),
) : LayoutWidget

/** Every key a widget can send, so the UI can tell which are unsupported. */
val LayoutWidget.keys: List<RemoteKey>
    get() =
        when (this) {
            is LayoutButton -> {
                when (val a = action) {
                    is ButtonAction.Key -> listOf(a.key)
                    is ButtonAction.Macro -> a.keys
                    is ButtonAction.LaunchApp, ButtonAction.OpenKeyboard -> emptyList()
                }
            }

            is DPadWidget -> {
                listOf(RemoteKey.UP, RemoteKey.DOWN, RemoteKey.LEFT, RemoteKey.RIGHT, RemoteKey.OK)
            }

            is RockerWidget -> {
                listOf(kind.up, kind.down)
            }

            is NumberPadWidget -> {
                NUMBER_PAD_KEYS
            }

            is MediaBarWidget -> {
                listOf(RemoteKey.REWIND, RemoteKey.PLAY_PAUSE, RemoteKey.FAST_FORWARD)
            }

            is SpacerWidget, is LabelWidget, is UnsupportedWidget -> {
                emptyList()
            }
        }

/** The number pad's keys, row by row: 1-9, then channel list, 0, previous channel. */
val NUMBER_PAD_KEYS: List<RemoteKey> =
    listOf(
        RemoteKey.DIGIT_1,
        RemoteKey.DIGIT_2,
        RemoteKey.DIGIT_3,
        RemoteKey.DIGIT_4,
        RemoteKey.DIGIT_5,
        RemoteKey.DIGIT_6,
        RemoteKey.DIGIT_7,
        RemoteKey.DIGIT_8,
        RemoteKey.DIGIT_9,
        RemoteKey.CHANNEL_LIST,
        RemoteKey.DIGIT_0,
        RemoteKey.PREVIOUS_CHANNEL,
    )

/** What pressing a [LayoutButton] does. */
@Serializable
sealed interface ButtonAction {
    @Serializable
    data class Key(
        val key: RemoteKey,
    ) : ButtonAction

    /**
     * Opens an app on the TV. [appId] is a [KnownApps] id, which the adapter translates, or an id
     * the TV itself listed. Needs [Capability.APP_LAUNCH].
     */
    @Serializable
    data class LaunchApp(
        val appId: String,
    ) : ButtonAction

    /** Sends several keys in a row, for example a shortcut to a channel number. */
    @Serializable
    data class Macro(
        val keys: List<RemoteKey>,
    ) : ButtonAction

    /** Opens the phone keyboard to type into the TV (SPEC F5). Needs [Capability.TEXT_INPUT]. */
    @Serializable
    data object OpenKeyboard : ButtonAction
}

/** Keys that make sense to send again while held: volume, channel, navigation and seeking (ButtonConfig). */
val REPEATABLE_KEYS: Set<RemoteKey> =
    setOf(
        RemoteKey.VOLUME_UP,
        RemoteKey.VOLUME_DOWN,
        RemoteKey.CHANNEL_UP,
        RemoteKey.CHANNEL_DOWN,
        RemoteKey.UP,
        RemoteKey.DOWN,
        RemoteKey.LEFT,
        RemoteKey.RIGHT,
        RemoteKey.REWIND,
        RemoteKey.FAST_FORWARD,
    )

/** Whether "Repeat while held" is offered for this action. */
val ButtonAction.canRepeat: Boolean get() = this is ButtonAction.Key && key in REPEATABLE_KEYS

/** Volume and channel repeat unless the user turns it off; other keys only when turned on. */
val ButtonAction.repeatsByDefault: Boolean
    get() =
        this is ButtonAction.Key &&
            key in setOf(RemoteKey.VOLUME_UP, RemoteKey.VOLUME_DOWN, RemoteKey.CHANNEL_UP, RemoteKey.CHANNEL_DOWN)

/** The capability a button needs to be usable on a given TV. */
val ButtonAction.requiredCapability: Capability
    get() =
        when (this) {
            is ButtonAction.Key, is ButtonAction.Macro -> Capability.KEYS
            is ButtonAction.LaunchApp -> Capability.APP_LAUNCH
            ButtonAction.OpenKeyboard -> Capability.TEXT_INPUT
        }
