package io.github.throughthewind.zapper.core.model

/**
 * The layout a new user starts from (SPEC FR-4.1, docs/design/components/RemoteScreen and
 * RemotePages), on the 4-column grid. Thumb-heavy controls sit in the lower two-thirds; Power sits
 * top-left, away from them. The top row follows RemotePages (Power, Source, Keyboard, Menu) rather
 * than RemoteScreen's Settings key, which no Samsung code is confirmed for.
 *
 * Pages follow RemotePages: Main, Apps (2x1 shortcuts to the bundled [KnownApps]), Numbers.
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
                                LayoutButton("keyboard", ButtonAction.OpenKeyboard, column = 2, row = 0),
                                key(RemoteKey.MENU, 3, 0),
                                DPadWidget("dpad", column = 0, row = 1),
                                key(RemoteKey.HOME, 3, 1),
                                key(RemoteKey.BACK, 3, 2),
                                key(RemoteKey.MUTE, 3, 3),
                                RockerWidget("volume", RockerKind.VOLUME, column = 0, row = 4),
                                app("netflix", 1, 4),
                                app("youtube", 1, 5),
                                key(RemoteKey.GUIDE, 1, 6),
                                key(RemoteKey.INFO, 2, 6),
                                RockerWidget("channel", RockerKind.CHANNEL, column = 3, row = 4),
                                MediaBarWidget("media", column = 0, row = 7),
                            ),
                    ),
                    RemotePage(
                        id = "apps",
                        name = "Apps",
                        widgets =
                            KnownApps.all.mapIndexed { index, known ->
                                app(known.id, column = (index % 2) * 2, row = index / 2)
                            },
                    ),
                    RemotePage(
                        id = "numbers",
                        name = "Numbers",
                        widgets = listOf(NumberPadWidget("numberpad", column = 0, row = 0)),
                    ),
                ),
        )

    /** A 2x1 shortcut to the known app [id] (docs/design/components/AppShortcut). */
    private fun app(
        id: String,
        column: Int,
        row: Int,
    ) = LayoutButton(
        id = id,
        action = ButtonAction.LaunchApp(id),
        column = column,
        row = row,
        columnSpan = 2,
        label = KnownApps.named(id)?.name,
    )

    private fun key(
        key: RemoteKey,
        column: Int,
        row: Int,
    ) = LayoutButton(id = key.name.lowercase(), action = ButtonAction.Key(key), column = column, row = row)
}
