package io.github.throughthewind.zapper

import io.github.throughthewind.zapper.core.remote.TvAdapter

/** Release builds ship no simulated TV. */
internal fun buildTypeAdapters(): List<TvAdapter> = emptyList()
