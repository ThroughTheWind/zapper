package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.TvDevice

/** Every brand adapter the app ships, looked up by id or by asking which one recognises a TV. */
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

    /** The first adapter that claims [candidate], with the device it describes. */
    fun recognise(candidate: DiscoveredTv): TvDevice? = byId.values.firstNotNullOfOrNull { it.recognise(candidate) }
}
