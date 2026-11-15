package io.github.throughthewind.zapper.adapters.roku

import io.github.throughthewind.zapper.core.model.KnownApps
import io.github.throughthewind.zapper.core.model.decodeHtmlEntities
import io.github.throughthewind.zapper.core.remote.TvApp

/**
 * Roku channel-store ids for each [KnownApps] entry. Unlike Samsung's, they do not change by year
 * or region. An id that is not here is taken as the Roku's own, from [parseApps].
 */
internal val ROKU_APP_IDS: Map<String, String> =
    mapOf(
        "netflix" to "12",
        "youtube" to "837",
        "prime-video" to "13",
        "disney-plus" to "291097",
        "apple-tv" to "551012",
        "spotify" to "22297",
        "plex" to "13535",
    )

internal fun rokuAppId(appId: String): String = ROKU_APP_IDS[appId] ?: appId

/**
 * The channels in `GET /query/apps`: `<app id="12" type="appl" version="…">Netflix</app>`. Only
 * `appl` entries are apps; `tvin` entries are the TV's inputs and `menu` its own screens.
 */
internal fun parseApps(xml: String): List<TvApp> =
    APP
        .findAll(xml)
        .filter { attribute(it.groupValues[1], "type").let { type -> type == null || type == "appl" } }
        .mapNotNull { match ->
            val id = attribute(match.groupValues[1], "id") ?: return@mapNotNull null
            TvApp(
                id = id,
                name =
                    match.groupValues[2]
                        .trim()
                        .decodeHtmlEntities()
                        .ifBlank { id },
            )
        }.toList()

private val APP = Regex("<app\\b([^>]*)>([^<]*)</app>")

private fun attribute(
    attributes: String,
    name: String,
): String? = Regex("""\b$name="([^"]*)"""").find(attributes)?.groupValues?.get(1)
