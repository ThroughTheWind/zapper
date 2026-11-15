package io.github.throughthewind.zapper

import io.github.throughthewind.zapper.core.remote.TvAdapter

/** Preview builds are for real TVs: like release, they ship no simulated TV. */
internal fun buildTypeAdapters(): List<TvAdapter> = emptyList()
