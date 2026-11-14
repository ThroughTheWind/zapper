package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.KnownApps

/**
 * Samsung's app ids for each [KnownApps] entry, newest first: ids change by year and region
 * (research §2.4). When the TV lists its apps, the first id it has wins; otherwise the newest.
 */
internal val SAMSUNG_APP_IDS: Map<String, List<String>> =
    mapOf(
        "netflix" to listOf("3201907018807", "11101200001"),
        "youtube" to listOf("111299001912"),
        "prime-video" to listOf("3201910019365", "3201512006785"),
        "disney-plus" to listOf("3201901017640"),
        "apple-tv" to listOf("3201807016597"),
        "spotify" to listOf("3201606009684"),
        "plex" to listOf("3201512006963"),
    )

/** The Samsung ids to try for [appId]: the table's for a known app, else [appId] itself. */
internal fun samsungAppIds(appId: String): List<String> = SAMSUNG_APP_IDS[appId] ?: listOf(appId)
