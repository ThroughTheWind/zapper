package io.github.throughthewind.zapper.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlEntitiesTest {
    @Test
    fun `named and numeric entities are decoded`() {
        assertEquals("Q&A <TV> \"living\" room's", "Q&amp;A &lt;TV&gt; &quot;living&quot; room&#39;s".decodeHtmlEntities())
        assertEquals("Séjour", "S&#xE9;jour".decodeHtmlEntities())
    }

    @Test
    fun `text without entities, or with unknown ones, is unchanged`() {
        assertEquals("Samsung 7 Series (75)", "Samsung 7 Series (75)".decodeHtmlEntities())
        assertEquals("Tom & Jerry &nbsp; &#99999999;", "Tom & Jerry &nbsp; &#99999999;".decodeHtmlEntities())
    }

    @Test
    fun `entities are detected`() {
        assertTrue("A &amp; B".hasHtmlEntities())
        assertFalse("A & B".hasHtmlEntities())
    }
}
