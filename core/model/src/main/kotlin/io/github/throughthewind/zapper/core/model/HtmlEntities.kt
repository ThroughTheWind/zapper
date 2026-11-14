package io.github.throughthewind.zapper.core.model

private val entity = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|amp|lt|gt|quot|apos);")

/**
 * Decodes the HTML entities some TVs put in the names they report, such as `&amp;` and `&#39;`,
 * so the user sees `Q&A` and not `Q&amp;A`. Anything that is not a known entity is left as is.
 */
fun String.decodeHtmlEntities(): String =
    entity.replace(this) { match ->
        val name = match.groupValues[1]
        when {
            name == "amp" -> "&"
            name == "lt" -> "<"
            name == "gt" -> ">"
            name == "quot" -> "\""
            name == "apos" -> "'"
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePoint) ?: match.value
            else -> name.drop(1).toIntOrNull()?.let(::codePoint) ?: match.value
        }
    }

/** True when the text still carries an entity [decodeHtmlEntities] would change. */
fun String.hasHtmlEntities(): Boolean = entity.containsMatchIn(this)

private fun codePoint(value: Int): String? = if (Character.isValidCodePoint(value)) String(Character.toChars(value)) else null
