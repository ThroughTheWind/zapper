package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.TvDevice
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge

/** Every brand adapter the app ships, looked up by id, and the entry point for discovery. */
class AdapterRegistry(
    adapters: List<TvAdapter>,
) {
    private val byId: Map<String, TvAdapter> =
        adapters.associateBy { it.id }.also {
            require(it.size == adapters.size) { "Two adapters share an id: ${adapters.map(TvAdapter::id)}" }
        }

    val adapters: Collection<TvAdapter> get() = byId.values

    operator fun get(id: String): TvAdapter? = byId[id]

    fun adapterFor(device: TvDevice): TvAdapter = byId[device.adapterId] ?: error("No adapter with id '${device.adapterId}'")

    /**
     * Every adapter's discovery merged, each TV emitted once. One adapter failing does not stop
     * the others.
     */
    fun discover(): Flow<DiscoveredTv> {
        val seen = mutableSetOf<String>()
        return byId.values
            .map { adapter -> adapter.discover().catch { } }
            .merge()
            .filter { synchronized(seen) { seen.add(it.identity) } }
    }

    /** Asks every adapter about [host] at once; the first that recognises it wins, in registration order. */
    suspend fun probe(host: String): DiscoveredTv? =
        coroutineScope {
            byId.values
                .map { adapter -> async { runCatching { adapter.probe(host) }.getOrNull() } }
                .firstNotNullOfOrNull { it.await() }
        }
}
