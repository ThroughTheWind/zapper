package io.github.throughthewind.zapper.core.model

/** The layout a new user starts from. Placeholder until the design system defines the real one. */
object DefaultLayouts {
    val standard: RemoteLayout =
        RemoteLayout(
            id = "standard",
            name = "Standard",
            columns = 3,
            buttons =
                listOf(
                    key(RemoteKey.POWER, 0, 0),
                    key(RemoteKey.SOURCE, 1, 0),
                    key(RemoteKey.HOME, 2, 0),
                    key(RemoteKey.UP, 1, 1),
                    key(RemoteKey.LEFT, 0, 2),
                    key(RemoteKey.OK, 1, 2),
                    key(RemoteKey.RIGHT, 2, 2),
                    key(RemoteKey.DOWN, 1, 3),
                    key(RemoteKey.BACK, 0, 4),
                    key(RemoteKey.MUTE, 1, 4),
                    key(RemoteKey.MENU, 2, 4),
                    key(RemoteKey.VOLUME_UP, 0, 5),
                    key(RemoteKey.CHANNEL_UP, 2, 5),
                    key(RemoteKey.VOLUME_DOWN, 0, 6),
                    key(RemoteKey.CHANNEL_DOWN, 2, 6),
                ),
        )

    private fun key(
        key: RemoteKey,
        column: Int,
        row: Int,
    ) = LayoutButton(id = key.name.lowercase(), action = ButtonAction.Key(key), column = column, row = row)
}
