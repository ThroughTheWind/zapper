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
import java.util.Base64

/** Saved TVs, layouts and encrypted pairing secrets. One instance per process. */
class ZapperRepository internal constructor(
    private val store: DataStore<StoredState>,
    private val cipher: SecretCipher,
) : CredentialStore {
    constructor(context: Context) : this(
        DataStoreFactory.create(StoredStateSerializer) { context.dataStoreFile("zapper.json") },
        KeystoreCipher(),
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

    /** A secret that no longer decrypts (the Keystore key was lost) reads as absent: the TV pairs again. */
    override suspend fun read(
        deviceId: String,
        name: String,
    ): String? {
        val sealed =
            store.data
                .first()
                .secrets[deviceId]
                ?.get(name) ?: return null
        return runCatching { cipher.decrypt(Base64.getDecoder().decode(sealed)).decodeToString() }.getOrNull()
    }

    override suspend fun write(
        deviceId: String,
        name: String,
        value: String,
    ) {
        val sealed = Base64.getEncoder().encodeToString(cipher.encrypt(value.encodeToByteArray()))
        store.updateData { state ->
            val forDevice = state.secrets[deviceId].orEmpty() + (name to sealed)
            state.copy(secrets = state.secrets + (deviceId to forDevice))
        }
    }

    override suspend fun remove(
        deviceId: String,
        name: String,
    ) {
        store.updateData { state ->
            val forDevice = state.secrets[deviceId].orEmpty() - name
            state.copy(secrets = if (forDevice.isEmpty()) state.secrets - deviceId else state.secrets + (deviceId to forDevice))
        }
    }

    override suspend fun clear(deviceId: String) {
        store.updateData { it.copy(secrets = it.secrets - deviceId) }
    }
}
