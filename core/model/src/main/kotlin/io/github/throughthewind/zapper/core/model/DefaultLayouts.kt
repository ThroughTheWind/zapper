package io.github.throughthewind.zapper.core.model

/**
 * The layout a new user starts from (SPEC FR-4.1, docs/design/components/RemoteScreen and
 * RemotePages), on the 4-column grid. Thumb-heavy controls sit in the lower two-thirds; Power sits
 * top-left, away from them.
 *
 * The design's Main page puts 2x1 Netflix and YouTube shortcuts between the rockers. App shortcuts
 * arrive with WI-009; until then those two rows stay empty rather than hold keys the design does
 * not place there.
 */
object DefaultLayouts {
    val standard: RemoteLayout =
        RemoteLayout(
            id = "standard",
            name = "Standard",
            columns = 4,
            pages =
                listOf(
                    RemotePage(
                        id = "main",
                        name = "Main",
                        widgets =
                            listOf(
                                key(RemoteKey.POWER, 0, 0),
                                key(RemoteKey.SOURCE, 1, 0),
                                key(RemoteKey.MENU, 2, 0),
                                key(RemoteKey.SETTINGS, 3, 0),
                                DPadWidget("dpad", column = 0, row = 1),
                                key(RemoteKey.HOME, 3, 1),
                                key(RemoteKey.BACK, 3, 2),
                                key(RemoteKey.MUTE, 3, 3),
                                RockerWidget("volume", RockerKind.VOLUME, column = 0, row = 4),
                                // Rows 4-5, columns 1-2: the Netflix and YouTube shortcuts (WI-009).
                                key(RemoteKey.GUIDE, 1, 6),
                                key(RemoteKey.INFO, 2, 6),
                                RockerWidget("channel", RockerKind.CHANNEL, column = 3, row = 4),
                                MediaBarWidget("media", column = 0, row = 7),
                            ),
                    ),
                    RemotePage(
                        id = "numbers",
                        name = "Numbers",
                        widgets = listOf(NumberPadWidget("numberpad", column = 0, row = 0)),
                    ),
                ),
        )

    private fun key(
        key: RemoteKey,
        column: Int,
        row: Int,
    ) = LayoutButton(id = key.name.lowercase(), action = ButtonAction.Key(key), column = column, row = row)
}
