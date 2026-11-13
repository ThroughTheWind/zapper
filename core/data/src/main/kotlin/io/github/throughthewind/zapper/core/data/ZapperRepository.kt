package io.github.throughthewind.zapper.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import io.github.throughthewind.zapper.core.model.DefaultLayouts
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.CredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Saved TVs, layouts and pairing tokens. One instance per process. */
class ZapperRepository internal constructor(
    private val store: DataStore<StoredState>,
) : CredentialStore {
    constructor(context: Context) : this(
        DataStoreFactory.create(StoredStateSerializer) { context.dataStoreFile("zapper.json") },
    )

    val devices: Flow<List<TvDevice>> = store.data.map { it.devices }

    val lastDevice: Flow<TvDevice?> = store.data.map { state -> state.devices.firstOrNull { it.id == state.lastDeviceId } }

    /** The user's layouts, or the standard one until they make their own. */
    val layouts: Flow<List<RemoteLayout>> = store.data.map { it.layouts.ifEmpty { listOf(DefaultLayouts.standard) } }

    suspend fun saveDevice(device: TvDevice) {
        store.updateData { state ->
            state.copy(devices = state.devices.filterNot { it.id == device.id } + device, lastDeviceId = device.id)
        }
    }

    suspend fun saveLayout(layout: RemoteLayout) {
        store.updateData { state -> state.copy(layouts = state.layouts.filterNot { it.id == layout.id } + layout) }
    }

    override suspend fun token(deviceId: String): String? = store.data.first().tokens[deviceId]

    override suspend fun saveToken(
        deviceId: String,
        token: String,
    ) {
        store.updateData { it.copy(tokens = it.tokens + (deviceId to token)) }
    }

    override suspend fun clear(deviceId: String) {
        store.updateData { it.copy(tokens = it.tokens - deviceId) }
    }
}
