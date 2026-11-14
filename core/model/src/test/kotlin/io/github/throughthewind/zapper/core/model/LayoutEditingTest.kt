package io.github.throughthewind.zapper.core.model

import io.github.throughthewind.zapper.core.model.LayoutEditing.Span
import io.github.throughthewind.zapper.core.model.LayoutEditing.addPage
import io.github.throughthewind.zapper.core.model.LayoutEditing.addWidget
import io.github.throughthewind.zapper.core.model.LayoutEditing.deletePage
import io.github.throughthewind.zapper.core.model.LayoutEditing.movePage
import io.github.throughthewind.zapper.core.model.LayoutEditing.moveWidget
import io.github.throughthewind.zapper.core.model.LayoutEditing.page
import io.github.throughthewind.zapper.core.model.LayoutEditing.removeWidget
import io.github.throughthewind.zapper.core.model.LayoutEditing.renamePage
import io.github.throughthewind.zapper.core.model.LayoutEditing.resizeWidget
import io.github.throughthewind.zapper.core.model.LayoutEditing.updateWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutEditingTest {
    private val layout =
        RemoteLayout(
            id = "tv",
            name = "TV",
            pages =
                listOf(
                    RemotePage(
                        "main",
                        "Main",
                        listOf(
                            LayoutButton("power", ButtonAction.Key(RemoteKey.POWER), column = 0, row = 0),
                            LayoutButton("home", ButtonAction.Key(RemoteKey.HOME), column = 1, row = 0),
                            DPadWidget("dpad", column = 0, row = 1),
                        ),
                    ),
                    RemotePage("apps", "Apps"),
                ),
        )

    private fun RemoteLayout.widget(id: String) = pages.flatMap { it.widgets }.single { it.id == id }

    @Test
    fun `a widget moves to a free cell`() {
        val moved = assertNotNull(layout.moveWidget("main", "power", column = 3, row = 0))

        assertEquals(3, moved.widget("power").column)
        assertEquals(0, moved.widget("power").row)
    }

    @Test
    fun `a move onto another widget or off the grid is refused`() {
        assertNull(layout.moveWidget("main", "power", column = 1, row = 0))
        assertNull(layout.moveWidget("main", "power", column = 2, row = 2), "onto the D-pad")
        assertNull(layout.moveWidget("main", "power", column = 4, row = 0))
        assertNull(layout.moveWidget("main", "power", column = -1, row = 0))
        assertNull(layout.moveWidget("main", "dpad", column = 2, row = 3), "the D-pad would leave the grid")
        assertNull(layout.moveWidget("main", "power", column = 0, row = LayoutEditing.MAX_ROWS))
    }

    @Test
    fun `resizing snaps to the widget's allowed spans only`() {
        val wide = assertNotNull(layout.resizeWidget("main", "home", Span(2, 1)))
        assertEquals(2, wide.widget("home").columnSpan)

        assertNull(layout.resizeWidget("main", "home", Span(3, 1)), "a key is 1x1 or 2x1")
        assertNull(layout.resizeWidget("main", "power", Span(2, 1)), "Home is in the way")

        val big = assertNotNull(layout.resizeWidget("main", "dpad", Span(4, 4)))
        assertEquals(4, big.widget("dpad").columnSpan)
        assertEquals(4, big.widget("dpad").rowSpan)
    }

    @Test
    fun `each widget type offers the sizes from the design system`() {
        assertEquals(listOf(Span(1, 2), Span(1, 3)), LayoutEditing.allowedSpans(RockerWidget("v", RockerKind.VOLUME, 0, 0)))
        assertEquals(listOf(Span(3, 4)), LayoutEditing.allowedSpans(NumberPadWidget("n", 0, 0)))
        assertEquals(
            listOf(Span(1, 1), Span(2, 1), Span(2, 2)),
            LayoutEditing.allowedSpans(LayoutButton("a", ButtonAction.LaunchApp("netflix"), 0, 0)),
        )
    }

    @Test
    fun `removing a widget frees its cells`() {
        val removed = assertNotNull(layout.removeWidget("main", "power"))

        assertTrue(removed.pages[0].widgets.none { it.id == "power" })
        assertNotNull(removed.moveWidget("main", "home", column = 0, row = 0))
        assertNull(removed.removeWidget("main", "power"), "already gone")
    }

    @Test
    fun `an added widget takes the first free place and a fresh id`() {
        val added = assertNotNull(layout.addWidget("main", LayoutButton("home", ButtonAction.Key(RemoteKey.HOME), 0, 0)))
        val copy = added.pages[0].widgets.last()

        assertEquals("home-2", copy.id)
        assertEquals(2 to 0, copy.column to copy.row)
    }

    @Test
    fun `a widget too big for what is left is refused`() {
        val full =
            (0 until LayoutEditing.MAX_ROWS).fold(layout.copy(pages = listOf(RemotePage("p", "P")))) { acc, row ->
                assertNotNull(acc.addWidget("p", MediaBarWidget("media", 0, row)))
            }

        assertNull(full.addWidget("p", LayoutButton("ok", ButtonAction.Key(RemoteKey.OK), 0, 0)))
    }

    @Test
    fun `button options change in place`() {
        val changed =
            assertNotNull(
                layout.updateWidget("main", "home") { (it as LayoutButton).copy(label = "Start", color = KeyColor.PRIMARY) },
            )

        val home = changed.widget("home") as LayoutButton
        assertEquals("Start", home.label)
        assertEquals(KeyColor.PRIMARY, home.color)
        assertEquals(1 to 0, home.column to home.row)
    }

    @Test
    fun `pages are added, renamed, reordered and deleted`() {
        val added = layout.addPage("Sport")
        val sport = added.pages.last()
        assertEquals("Sport", sport.name)
        assertEquals(3, added.pages.size)

        val renamed = assertNotNull(added.renamePage(sport.id, "Football"))
        assertEquals("Football", renamed.page(sport.id)?.name)
        assertNull(added.renamePage(sport.id, "  "), "a page needs a name")

        val moved = assertNotNull(renamed.movePage(sport.id, 0))
        assertEquals(listOf(sport.id, "main", "apps"), moved.pages.map { it.id })

        val deleted = assertNotNull(moved.deletePage("main"))
        assertEquals(listOf(sport.id, "apps"), deleted.pages.map { it.id })
    }

    @Test
    fun `the last page cannot be deleted`() {
        val one = assertNotNull(layout.deletePage("apps"))

        assertNull(one.deletePage("main"))
    }

    @Test
    fun `new pages get ids no other page has`() {
        val twice = layout.addPage("A").addPage("B")

        assertEquals(
            twice.pages.size,
            twice.pages
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun `volume repeats by default, OK only when asked, apps never`() {
        assertTrue(LayoutButton("v", ButtonAction.Key(RemoteKey.VOLUME_UP), 0, 0).repeats)
        assertFalse(LayoutButton("v", ButtonAction.Key(RemoteKey.VOLUME_UP), 0, 0, repeat = false).repeats)
        assertFalse(LayoutButton("ok", ButtonAction.Key(RemoteKey.OK), 0, 0).repeats)
        assertFalse(LayoutButton("ok", ButtonAction.Key(RemoteKey.OK), 0, 0, repeat = true).repeats, "OK is not repeatable")
        assertTrue(LayoutButton("up", ButtonAction.Key(RemoteKey.UP), 0, 0, repeat = true).repeats)
        assertFalse(LayoutButton("n", ButtonAction.LaunchApp("netflix"), 0, 0, repeat = true).repeats)
    }
}

class EditHistoryTest {
    @Test
    fun `undo goes back one edit at a time`() {
        val history = EditHistory(1).edit(2).edit(3)

        assertEquals(2, history.undo().current)
        assertEquals(1, history.undo().undo().current)
        assertFalse(history.undo().undo().canUndo)
        assertEquals(
            1,
            history
                .undo()
                .undo()
                .undo()
                .current,
            "nothing left to undo",
        )
    }

    @Test
    fun `an edit that changes nothing is not recorded`() {
        val history = EditHistory("a").edit("a")

        assertFalse(history.canUndo)
        assertFalse(history.changed)
    }

    @Test
    fun `changed is false once every edit is undone`() {
        val history = EditHistory("a").edit("b")

        assertTrue(history.changed)
        assertFalse(history.undo().changed)
        assertFalse(EditHistory("a").edit("b").edit("a").changed, "back where it started")
    }

    @Test
    fun `the history keeps a bounded number of edits`() {
        val history = (1..10).fold(EditHistory(0, limit = 3)) { acc, n -> acc.edit(n) }

        assertEquals(
            7,
            history
                .undo()
                .undo()
                .undo()
                .current,
        )
        assertFalse(
            history
                .undo()
                .undo()
                .undo()
                .canUndo,
        )
    }
}

class ButtonCatalogTest {
    @Test
    fun `every group of the spec has items and composites are single items`() {
        val items = ButtonCatalog.items()

        CatalogGroup.entries.forEach { group -> assertTrue(items.any { it.group == group }, group.title) }
        assertTrue(items.any { it.widget is DPadWidget })
        assertTrue(items.any { it.widget is NumberPadWidget })
        assertEquals(2, items.count { it.widget is RockerWidget })
    }

    @Test
    fun `installed apps come first and use the known id when the name matches`() {
        val items = ButtonCatalog.items(listOf(CatalogApp("3201907018807", "Netflix"), CatalogApp("111299001912", "Arte")))
        val apps = items.filter { it.group == CatalogGroup.APPS }.map { (it.widget as LayoutButton).action as ButtonAction.LaunchApp }

        assertEquals(listOf("netflix", "111299001912"), apps.take(2).map { it.appId })
        assertEquals(1, apps.count { it.appId == "netflix" })
        assertEquals(KnownApps.all.size + 1, apps.size)
    }

    @Test
    fun `buttons the TV does not support are hidden`() {
        val items = ButtonCatalog.items()
        val available = ButtonCatalog.available(items, capabilities = setOf(Capability.KEYS)) { it != RemoteKey.SETTINGS }

        assertTrue(available.none { (it.widget as? LayoutButton)?.action == ButtonAction.Key(RemoteKey.SETTINGS) })
        assertTrue(available.none { it.group == CatalogGroup.APPS }, "no app launch")
        assertTrue(available.none { (it.widget as? LayoutButton)?.action == ButtonAction.OpenKeyboard }, "no text input")
        assertTrue(available.any { it.widget is SpacerWidget })
    }
}
