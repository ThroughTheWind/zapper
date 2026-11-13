package io.github.throughthewind.zapper

import io.github.throughthewind.zapper.adapters.fake.FakeTvAdapter
import io.github.throughthewind.zapper.core.remote.TvAdapter

/** Debug builds add the simulated TV (SPEC FR-11.3). Add it by IP with 192.0.2.1, or find it by scanning. */
internal fun buildTypeAdapters(): List<TvAdapter> = listOf(FakeTvAdapter())
