package io.github.throughthewind.zapper.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LayoutFileTest {
    private val custom =
        RemoteLayout(
            id = "living-room",
            name = "Living room",
            pages =
                DefaultLayouts.standard.pages +
                    RemotePage(
                        "page-1",
                        "Sport",
                        listOf(
                            LabelWidget("label", "Football", column = 0, row = 0),
                            LayoutButton(
                                "ch",
                                ButtonAction.Macro(listOf(RemoteKey.DIGIT_4, RemoteKey.DIGIT_2)),
                                column = 0,
                                row = 1,
                                columnSpan = 2,
                                label = "beIN",
                                icon = "sports",
                                color = KeyColor.TERTIARY,
                            ),
                            LayoutButton("up", ButtonAction.Key(RemoteKey.UP), column = 2, row = 1, repeat = true),
                            SpacerWidget("gap", column = 0, row = 2, columnSpan = 4),
                        ),
                    ),
        )

    @Test
    fun `export then import reproduces the layout exactly`() {
        val imported = LayoutFile.decode(LayoutFile.encode(custom))

        assertEquals(custom, imported.layout)
        assertEquals(0, imported.unsupported)
    }

    @Test
    fun `the file names its format and version`() {
        val text = LayoutFile.encode(custom)

        assertTrue("\"format\": \"zapper-layout\"" in text, text.take(80))
        assertTrue("\"version\": 1" in text)
    }

    @Test
    fun `a file from a newer version is refused with a reason`() {
        val text = LayoutFile.encode(custom).replace("\"version\": 1", "\"version\": 2")

        val error = assertFailsWith<LayoutFileException> { LayoutFile.decode(text) }
        assertTrue("newer version" in error.message.orEmpty())
    }

    @Test
    fun `a file that is not a layout is refused`() {
        assertFailsWith<LayoutFileException> { LayoutFile.decode("not json") }
        assertFailsWith<LayoutFileException> { LayoutFile.decode("""{"format":"something-else","version":1}""") }
        assertFailsWith<LayoutFileException> { LayoutFile.decode("""{"format":"zapper-layout","version":1,"layout":{"pages":[]}}""") }
    }

    @Test
    fun `unknown widgets and keys become placeholders, not dropped`() {
        val text =
            """
            {"format":"zapper-layout","version":1,"layout":{"id":"x","name":"X","pages":[{"id":"p","name":"P","widgets":[
              {"type":"button","id":"ok","action":{"type":"io.github.throughthewind.zapper.core.model.ButtonAction.Key","key":"OK"},"column":0,"row":0},
              {"type":"touchpad","id":"pad","column":0,"row":1,"columnSpan":4,"rowSpan":3},
              {"type":"button","id":"ambi","action":{"type":"io.github.throughthewind.zapper.core.model.ButtonAction.Key","key":"AMBILIGHT"},"column":1,"row":0}
            ]}]}}
            """.trimIndent()

        val imported = LayoutFile.decode(text)
        val widgets =
            imported.layout.pages
                .single()
                .widgets

        assertEquals(2, imported.unsupported)
        assertEquals(listOf("ok", "pad", "ambi"), widgets.map { it.id })
        val pad = assertIs<UnsupportedWidget>(widgets[1])
        assertEquals(4 to 3, pad.columnSpan to pad.rowSpan)
        assertIs<UnsupportedWidget>(widgets[2])
    }

    @Test
    fun `a placeholder is written back so a newer version can read it again`() {
        val text =
            """
            {"format":"zapper-layout","version":1,"layout":{"id":"x","name":"X","pages":[{"id":"p","name":"P","widgets":[
              {"type":"touchpad","id":"pad","column":0,"row":0,"columnSpan":4,"rowSpan":3}
            ]}]}}
            """.trimIndent()
        val once = LayoutFile.decode(text).layout

        val again = LayoutFile.decode(LayoutFile.encode(once)).layout

        assertEquals(once, again)
        val pad =
            assertIs<UnsupportedWidget>(
                again.pages
                    .single()
                    .widgets
                    .single(),
            )
        assertEquals("touchpad", pad.original["type"].toString().trim('"'))
    }

    @Test
    fun `overlapping widgets are moved to a free place`() {
        val text =
            """
            {"format":"zapper-layout","version":1,"layout":{"id":"x","name":"X","pages":[{"id":"p","name":"P","widgets":[
              {"type":"button","id":"a","action":{"type":"io.github.throughthewind.zapper.core.model.ButtonAction.Key","key":"OK"},"column":0,"row":0},
              {"type":"button","id":"b","action":{"type":"io.github.throughthewind.zapper.core.model.ButtonAction.Key","key":"HOME"},"column":0,"row":0}
            ]}]}}
            """.trimIndent()

        val widgets =
            LayoutFile
                .decode(text)
                .layout.pages
                .single()
                .widgets

        assertEquals(listOf(0 to 0, 1 to 0), widgets.map { it.column to it.row })
    }
}
