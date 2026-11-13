package io.github.throughthewind.zapper.core.model

import kotlinx.serialization.Serializable

/**
 * A user-editable remote: a grid of buttons the user can add, move, resize and relabel.
 * Stored as JSON, so every field needs a default that keeps old layouts readable.
 */
@Serializable
data class RemoteLayout(
    val id: String,
    val name: String,
    val columns: Int = 4,
    val buttons: List<LayoutButton> = emptyList(),
) {
    init {
        require(columns > 0) { "columns must be positive, was $columns" }
    }
}

/** One button placed on a [RemoteLayout] grid. Position and size are in grid cells. */
@Serializable
data class LayoutButton(
    val id: String,
    val action: ButtonAction,
    val column: Int,
    val row: Int,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val label: String? = null,
    val icon: String? = null,
)

/** What pressing a [LayoutButton] does. */
@Serializable
sealed interface ButtonAction {
    @Serializable
    data class Key(
        val key: RemoteKey,
    ) : ButtonAction

    /** Opens an app on the TV. Needs [Capability.APP_LAUNCH]. */
    @Serializable
    data class LaunchApp(
        val appId: String,
    ) : ButtonAction

    /** Sends several keys in a row, for example a shortcut to a channel number. */
    @Serializable
    data class Macro(
        val keys: List<RemoteKey>,
    ) : ButtonAction
}

/** The capability a button needs to be usable on a given TV. */
val ButtonAction.requiredCapability: Capability
    get() =
        when (this) {
            is ButtonAction.Key, is ButtonAction.Macro -> Capability.KEYS
            is ButtonAction.LaunchApp -> Capability.APP_LAUNCH
        }
