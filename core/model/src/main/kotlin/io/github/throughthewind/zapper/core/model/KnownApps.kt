package io.github.throughthewind.zapper.core.model

/**
 * A streaming app a layout can name without knowing the TV's brand (SPEC FR-6.1). Each adapter
 * maps [id] to its platform's own app id, so a "Netflix" shortcut keeps working on another TV.
 */
data class KnownApp(
    val id: String,
    val name: String,
)

/** The bundled list of well-known apps, used when the TV does not list its own. */
object KnownApps {
    val all: List<KnownApp> =
        listOf(
            KnownApp("netflix", "Netflix"),
            KnownApp("youtube", "YouTube"),
            KnownApp("prime-video", "Prime Video"),
            KnownApp("disney-plus", "Disney+"),
            KnownApp("apple-tv", "Apple TV"),
            KnownApp("spotify", "Spotify"),
            KnownApp("plex", "Plex"),
        )

    /** The known app with [id], or null for an app id that came from a TV. */
    fun named(id: String): KnownApp? = all.firstOrNull { it.id == id }
}
