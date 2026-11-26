package io.github.throughthewind.zapper.core.remote

/**
 * Copy an adapter shows the user, such as [TvAdapter.powerOnHelp]. Adapters are plain JVM
 * modules without Android resources, so they carry their translations themselves: [english], and
 * [translations] keyed by language code ("fr", "pl").
 */
data class LocalizedText(
    val english: String,
    val translations: Map<String, String> = emptyMap(),
) {
    /** The text for [languageTag] ("fr", "fr-CA", "pl-PL"), or English when it has none. */
    fun forLanguage(languageTag: String): String =
        translations[languageTag.substringBefore('-').substringBefore('_').lowercase()] ?: english
}
