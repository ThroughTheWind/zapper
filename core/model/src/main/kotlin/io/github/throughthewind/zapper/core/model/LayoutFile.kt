package io.github.throughthewind.zapper.core.model

import io.github.throughthewind.zapper.core.model.LayoutEditing.movedTo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Why a file could not be imported, in words for the user. */
class LayoutFileException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** A layout read from a file, and how many of its widgets this version cannot run. */
data class ImportedLayout(
    val layout: RemoteLayout,
    val unsupported: Int,
)

/**
 * The layout file format (SPEC F12): a versioned JSON envelope around a [RemoteLayout].
 *
 * ```json
 * { "format": "zapper-layout", "version": 1, "layout": { "id": …, "name": …, "pages": [ … ] } }
 * ```
 *
 * Import checks the version (FR-12.2). Widgets this version cannot read, such as a newer widget
 * type or a key it does not know, become [UnsupportedWidget] placeholders rather than being
 * dropped, and are written back unchanged on export.
 */
object LayoutFile {
    const val FORMAT = "zapper-layout"
    const val VERSION = 1
    const val MIME_TYPE = "application/json"

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

    fun encode(layout: RemoteLayout): String {
        val envelope =
            buildJsonObject {
                put("format", FORMAT)
                put("version", VERSION)
                put("layout", json.encodeToJsonElement(RemoteLayout.serializer(), layout))
            }
        return json.encodeToString(JsonElement.serializer(), envelope)
    }

    /** Reads a layout file. Throws [LayoutFileException] when it is not one this version can read. */
    fun decode(text: String): ImportedLayout {
        val root =
            try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (e: SerializationException) {
                throw LayoutFileException(NOT_A_LAYOUT, e)
            } ?: throw LayoutFileException(NOT_A_LAYOUT)
        if (root.string("format") != FORMAT) throw LayoutFileException(NOT_A_LAYOUT)
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: throw LayoutFileException(DAMAGED)
        if (version > VERSION) throw LayoutFileException("This layout was made by a newer version of Zapper. Update Zapper to import it.")
        if (version < 1) throw LayoutFileException(DAMAGED)
        val body = root["layout"] as? JsonObject ?: throw LayoutFileException(DAMAGED)
        return try {
            readLayout(body)
        } catch (e: IllegalArgumentException) {
            throw LayoutFileException(DAMAGED, e)
        }
    }

    private fun readLayout(body: JsonObject): ImportedLayout {
        val columns = (body["columns"] as? JsonPrimitive)?.intOrNull ?: 4
        require(columns in 1..MAX_COLUMNS) { "columns out of range: $columns" }
        var unsupported = 0
        val pages =
            (body["pages"] as? JsonArray).orEmpty().mapIndexed { index, element ->
                val page = element as? JsonObject ?: throw IllegalArgumentException("page $index is not an object")
                val read = (page["widgets"] as? JsonArray).orEmpty().map { readWidget(it) }
                unsupported += read.count { it is UnsupportedWidget }
                place(
                    RemotePage(
                        id = page.string("id") ?: "page-${index + 1}",
                        name = page.string("name") ?: "Page ${index + 1}",
                    ),
                    read,
                    columns,
                )
            }
        require(pages.isNotEmpty()) { "a layout needs a page" }
        require(pages.map { it.id }.toSet().size == pages.size) { "two pages share an id" }
        val layout =
            RemoteLayout(
                id = body.string("id") ?: "imported",
                name = body.string("name") ?: "Imported",
                columns = columns,
                pages = pages,
            )
        return ImportedLayout(layout, unsupported)
    }

    /** A widget this version reads, or a placeholder that keeps it. */
    private fun readWidget(element: JsonElement): LayoutWidget {
        val obj = element as? JsonObject ?: throw IllegalArgumentException("a widget is not an object")
        val known = runCatching { json.decodeFromJsonElement(LayoutWidget.serializer(), obj) }.getOrNull()
        if (known is UnsupportedWidget) {
            // Written by a version that could not read it: this one may.
            return runCatching { json.decodeFromJsonElement(LayoutWidget.serializer(), known.original) }
                .getOrNull()
                ?.takeUnless { it is UnsupportedWidget }
                ?: known
        }
        if (known != null) return known
        return UnsupportedWidget(
            id = obj.string("id") ?: throw IllegalArgumentException("a widget has no id"),
            column = obj.int("column") ?: 0,
            row = obj.int("row") ?: 0,
            columnSpan = (obj.int("columnSpan") ?: obj.int("size") ?: 1).coerceIn(1, MAX_COLUMNS),
            rowSpan = (obj.int("rowSpan") ?: obj.int("size") ?: 1).coerceIn(1, LayoutEditing.MAX_ROWS),
            original = obj,
        )
    }

    /**
     * Puts the widgets on the page in file order. One that overlaps an earlier one or leaves the
     * grid moves to the first free place; ids are made unique, so edit mode's rules hold.
     */
    private fun place(
        empty: RemotePage,
        widgets: List<LayoutWidget>,
        columns: Int,
    ): RemotePage =
        widgets.fold(empty) { page, widget ->
            val unique =
                if (page.widgets.any { it.id == widget.id }) {
                    with(LayoutEditing) { widget.withId(widget.id + "-" + (page.widgets.size + 1)) }
                } else {
                    widget
                }
            val placed =
                if (LayoutEditing.fits(page, unique, columns)) {
                    unique
                } else {
                    LayoutEditing.firstFreeSlot(page, unique, columns)?.let { (c, r) -> unique.movedTo(c, r) }
                }
            if (placed == null) page else page.copy(widgets = page.widgets + placed)
        }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private const val MAX_COLUMNS = 6
    private const val NOT_A_LAYOUT = "This file is not a Zapper layout."
    private const val DAMAGED = "This layout file is damaged and cannot be imported."
}
