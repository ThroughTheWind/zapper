package io.github.throughthewind.zapper.core.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizedTextTest {
    private val text = LocalizedText("Hello", mapOf("fr" to "Bonjour", "pl" to "Cześć"))

    @Test
    fun `a language it has is used whatever the region`() {
        assertEquals("Bonjour", text.forLanguage("fr"))
        assertEquals("Bonjour", text.forLanguage("fr-CA"))
        assertEquals("Cześć", text.forLanguage("pl-PL"))
    }

    @Test
    fun `any other language falls back to English`() {
        assertEquals("Hello", text.forLanguage("de-DE"))
        assertEquals("Hello", text.forLanguage("en"))
        assertEquals("Hello", text.forLanguage(""))
    }
}
