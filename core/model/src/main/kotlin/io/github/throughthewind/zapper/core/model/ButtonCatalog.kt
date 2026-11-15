package io.github.throughthewind.zapper.core.model

/** The catalog's groups, in the spec's order (SPEC FR-8.3). */
enum class CatalogGroup(
    val title: String,
) {
    NAVIGATION("Navigation"),
    VOLUME_CHANNEL("Volume & Channel"),
    MEDIA("Media"),
    NUMBERS("Numbers"),
    APPS("Apps"),
    SYSTEM("System"),
    SPACER_LABEL("Spacer/Label"),
}

/** Something the catalog can add: a template [widget] at (0, 0), placed for real when added. */
data class CatalogItem(
    val group: CatalogGroup,
    val title: String,
    val widget: LayoutWidget,
)

/** An app the TV listed, offered in the Apps group. */
data class CatalogApp(
    val id: String,
    val name: String,
)

/**
 * The buttons and composite controls edit mode can add (docs/design/components/ButtonCatalog).
 * Composite controls (D-pad, rockers, number pad, media bar) are single items (FR-8.8).
 */
object ButtonCatalog {
    /**
     * Every item, with the Apps group listing the TV's [installed] apps first, then the bundled
     * ones it did not list. An installed app with a known app's name uses the known id, so the
     * layout keeps working on another brand.
     */
    fun items(installed: List<CatalogApp> = emptyList()): List<CatalogItem> = fixed + apps(installed)

    /** The items the current TV can run: buttons it does not support are hidden (FR-4.5). */
    fun available(
        items: List<CatalogItem>,
        capabilities: Set<Capability>,
        supports: (RemoteKey) -> Boolean,
    ): List<CatalogItem> =
        items.filter { item ->
            val widget = item.widget
            val action = (widget as? LayoutButton)?.action
            when {
                action != null && action !is ButtonAction.Key && action !is ButtonAction.Macro -> action.requiredCapability in capabilities
                else -> widget.keys.all(supports)
            }
        }

    private fun apps(installed: List<CatalogApp>): List<CatalogItem> {
        val fromTv =
            installed.map { app ->
                val known = KnownApps.all.firstOrNull { it.name.equals(app.name, ignoreCase = true) }
                CatalogApp(known?.id ?: app.id, known?.name ?: app.name)
            }
        val bundled = KnownApps.all.filter { known -> fromTv.none { it.id == known.id } }.map { CatalogApp(it.id, it.name) }
        return (fromTv + bundled).distinctBy { it.id }.map { app ->
            CatalogItem(
                CatalogGroup.APPS,
                app.name,
                LayoutButton(
                    id = app.id.toWidgetId(),
                    action = ButtonAction.LaunchApp(app.id),
                    column = 0,
                    row = 0,
                    columnSpan = 2,
                    label = app.name,
                ),
            )
        }
    }

    private val fixed: List<CatalogItem> =
        listOf(
            CatalogItem(CatalogGroup.NAVIGATION, "D-pad", DPadWidget("dpad", 0, 0)),
            key(CatalogGroup.NAVIGATION, RemoteKey.UP, "Up"),
            key(CatalogGroup.NAVIGATION, RemoteKey.DOWN, "Down"),
            key(CatalogGroup.NAVIGATION, RemoteKey.LEFT, "Left"),
            key(CatalogGroup.NAVIGATION, RemoteKey.RIGHT, "Right"),
            key(CatalogGroup.NAVIGATION, RemoteKey.OK, "OK"),
            key(CatalogGroup.NAVIGATION, RemoteKey.HOME, "Home"),
            key(CatalogGroup.NAVIGATION, RemoteKey.BACK, "Back"),
            key(CatalogGroup.NAVIGATION, RemoteKey.EXIT, "Exit"),
            key(CatalogGroup.NAVIGATION, RemoteKey.MENU, "Menu"),
            CatalogItem(CatalogGroup.VOLUME_CHANNEL, "Volume rocker", RockerWidget("volume", RockerKind.VOLUME, 0, 0)),
            CatalogItem(CatalogGroup.VOLUME_CHANNEL, "Channel rocker", RockerWidget("channel", RockerKind.CHANNEL, 0, 0)),
            key(CatalogGroup.VOLUME_CHANNEL, RemoteKey.VOLUME_UP, "Volume up"),
            key(CatalogGroup.VOLUME_CHANNEL, RemoteKey.VOLUME_DOWN, "Volume down"),
            key(CatalogGroup.VOLUME_CHANNEL, RemoteKey.MUTE, "Mute"),
            key(CatalogGroup.VOLUME_CHANNEL, RemoteKey.CHANNEL_UP, "Channel up"),
            key(CatalogGroup.VOLUME_CHANNEL, RemoteKey.CHANNEL_DOWN, "Channel down"),
            key(CatalogGroup.VOLUME_CHANNEL, RemoteKey.CHANNEL_LIST, "Channel list"),
            key(CatalogGroup.VOLUME_CHANNEL, RemoteKey.PREVIOUS_CHANNEL, "Previous channel"),
            CatalogItem(CatalogGroup.MEDIA, "Media controls", MediaBarWidget("media", 0, 0)),
            key(CatalogGroup.MEDIA, RemoteKey.PLAY_PAUSE, "Play/Pause"),
            key(CatalogGroup.MEDIA, RemoteKey.PLAY, "Play"),
            key(CatalogGroup.MEDIA, RemoteKey.PAUSE, "Pause"),
            key(CatalogGroup.MEDIA, RemoteKey.STOP, "Stop"),
            key(CatalogGroup.MEDIA, RemoteKey.REWIND, "Rewind"),
            key(CatalogGroup.MEDIA, RemoteKey.FAST_FORWARD, "Fast forward"),
            CatalogItem(CatalogGroup.NUMBERS, "Number pad", NumberPadWidget("numberpad", 0, 0)),
        ) +
            (0..9).map { key(CatalogGroup.NUMBERS, RemoteKey.valueOf("DIGIT_$it"), "$it") } +
            listOf(
                key(CatalogGroup.SYSTEM, RemoteKey.POWER, "Power"),
                key(CatalogGroup.SYSTEM, RemoteKey.SOURCE, "Source"),
                key(CatalogGroup.SYSTEM, RemoteKey.SETTINGS, "Settings"),
                key(CatalogGroup.SYSTEM, RemoteKey.INFO, "Info"),
                key(CatalogGroup.SYSTEM, RemoteKey.GUIDE, "Guide"),
                CatalogItem(CatalogGroup.SYSTEM, "Keyboard", LayoutButton("keyboard", ButtonAction.OpenKeyboard, 0, 0)),
                key(CatalogGroup.SYSTEM, RemoteKey.RED, "Red"),
                key(CatalogGroup.SYSTEM, RemoteKey.GREEN, "Green"),
                key(CatalogGroup.SYSTEM, RemoteKey.YELLOW, "Yellow"),
                key(CatalogGroup.SYSTEM, RemoteKey.BLUE, "Blue"),
                CatalogItem(CatalogGroup.SPACER_LABEL, "Spacer", SpacerWidget("spacer", 0, 0)),
                CatalogItem(CatalogGroup.SPACER_LABEL, "Label", LabelWidget("label", "Label", 0, 0)),
            )

    private fun key(
        group: CatalogGroup,
        key: RemoteKey,
        title: String,
    ) = CatalogItem(group, title, LayoutButton(key.name.lowercase(), ButtonAction.Key(key), 0, 0))

    private fun String.toWidgetId(): String = "app-" + lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
