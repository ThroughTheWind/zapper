package io.github.throughthewind.zapper.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteLayoutTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `layout survives a JSON round trip`() {
        val layout =
            RemoteLayout(
                id = "default",
                name = "Default",
                buttons =
                    listOf(
                        LayoutButton("power", ButtonAction.Key(RemoteKey.POWER), column = 0, row = 0),
                        LayoutButton("netflix", ButtonAction.LaunchApp("netflix"), column = 1, row = 0, label = "Netflix"),
                        LayoutButton("ch42", ButtonAction.Macro(listOf(RemoteKey.DIGIT_4, RemoteKey.DIGIT_2)), column = 2, row = 0),
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
    }
}

class DefaultLayoutsTest {
    @Test
    fun `no two buttons of the standard layout share a cell or an id`() {
        val buttons = DefaultLayouts.standard.buttons

        assertEquals(buttons.size, buttons.map { it.column to it.row }.toSet().size)
        assertEquals(buttons.size, buttons.map { it.id }.toSet().size)
        buttons.forEach { kotlin.test.assertTrue(it.column < DefaultLayouts.standard.columns, it.id) }
    }
}
