package io.github.throughthewind.zapper.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteLayoutTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `layout survives a JSON round trip`() {
        val layout =
            RemoteLayout(
                id = "default",
                name = "Default",
                pages =
                    listOf(
                        RemotePage(
                            "main",
                            "Main",
                            listOf(
                                LayoutButton("power", ButtonAction.Key(RemoteKey.POWER), column = 0, row = 0),
                                LayoutButton("netflix", ButtonAction.LaunchApp("netflix"), column = 1, row = 0, label = "Netflix"),
                                LayoutButton("ch42", ButtonAction.Macro(listOf(RemoteKey.DIGIT_4, RemoteKey.DIGIT_2)), column = 2, row = 0),
                                DPadWidget("dpad", column = 0, row = 1, size = 4),
                                RockerWidget("vol", RockerKind.VOLUME, column = 0, row = 5, rowSpan = 2),
                                NumberPadWidget("pad", column = 1, row = 5),
                                MediaBarWidget("media", column = 0, row = 9, columnSpan = 3),
                            ),
                        ),
                    ),
            )

        val decoded = json.decodeFromString<RemoteLayout>(json.encodeToString(layout))

        assertEquals(layout, decoded)
    }

    @Test
    fun `a layout needs at least one column`() {
        assertFailsWith<IllegalArgumentException> { RemoteLayout(id = "x", name = "x", columns = 0) }
    }

    @Test
    fun `app shortcuts need app launch, keys and macros need keys`() {
        assertEquals(Capability.KEYS, ButtonAction.Key(RemoteKey.OK).requiredCapability)
        assertEquals(Capability.KEYS, ButtonAction.Macro(emptyList()).requiredCapability)
        assertEquals(Capability.APP_LAUNCH, ButtonAction.LaunchApp("youtube").requiredCapability)
        assertEquals(Capability.TEXT_INPUT, ButtonAction.OpenKeyboard.requiredCapability)
    }

    @Test
    fun `the keyboard button survives a JSON round trip`() {
        val button = LayoutButton("keyboard", ButtonAction.OpenKeyboard, column = 2, row = 0)

        assertEquals(button, json.decodeFromString<LayoutWidget>(json.encodeToString<LayoutWidget>(button)))
    }
}

class DefaultLayoutsTest {
    private val layout = DefaultLayouts.standard

    private fun LayoutWidget.cells() = (column until column + columnSpan).flatMap { c -> (row until row + rowSpan).map { r -> c to r } }

    @Test
    fun `no two widgets of a page overlap, share an id, or leave the grid`() {
        layout.pages.forEach { page ->
            val cells = page.widgets.flatMap { it.cells() }
            assertEquals(cells.size, cells.toSet().size, "overlap on ${page.id}")
            assertEquals(
                page.widgets.size,
                page.widgets
                    .map { it.id }
                    .toSet()
                    .size,
            )
            page.widgets.forEach { assertTrue(it.column >= 0 && it.column + it.columnSpan <= layout.columns, it.id) }
        }
    }

    @Test
    fun `the default remote has every key the spec asks for`() {
        val keys = layout.pages.flatMap { page -> page.widgets.flatMap { it.keys } }.toSet()
        val required =
            setOf(
                RemoteKey.POWER,
                RemoteKey.SOURCE,
                RemoteKey.HOME,
                RemoteKey.BACK,
                RemoteKey.MENU,
                RemoteKey.UP,
                RemoteKey.DOWN,
                RemoteKey.LEFT,
                RemoteKey.RIGHT,
                RemoteKey.OK,
                RemoteKey.VOLUME_UP,
                RemoteKey.VOLUME_DOWN,
                RemoteKey.MUTE,
                RemoteKey.CHANNEL_UP,
                RemoteKey.CHANNEL_DOWN,
                RemoteKey.PLAY_PAUSE,
                RemoteKey.REWIND,
                RemoteKey.FAST_FORWARD,
                RemoteKey.INFO,
                RemoteKey.GUIDE,
            ) + (0..9).map { RemoteKey.valueOf("DIGIT_$it") }
        assertEquals(emptySet(), required - keys)
    }

    @Test
    fun `the main page has a keyboard button`() {
        assertTrue(
            layout.pages
                .first()
                .widgets
                .any { it is LayoutButton && it.action == ButtonAction.OpenKeyboard },
        )
    }

    @Test
    fun `numbers are on their own page`() {
        assertEquals(listOf("main", "numbers"), layout.pages.map { it.id })
        assertTrue(layout.pages[1].widgets.single() is NumberPadWidget)
    }
}

class WidgetTest {
    @Test
    fun `widgets reject sizes the design does not allow`() {
        assertFailsWith<IllegalArgumentException> { DPadWidget("d", 0, 0, size = 2) }
        assertFailsWith<IllegalArgumentException> { RockerWidget("r", RockerKind.VOLUME, 0, 0, rowSpan = 4) }
        assertFailsWith<IllegalArgumentException> { MediaBarWidget("m", 0, 0, columnSpan = 2) }
    }
}
