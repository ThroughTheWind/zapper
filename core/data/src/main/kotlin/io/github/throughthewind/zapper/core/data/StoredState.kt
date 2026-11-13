package io.github.throughthewind.zapper.core.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import io.github.throughthewind.zapper.core.model.RemoteLayout
import io.github.throughthewind.zapper.core.model.TvDevice
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Everything Zapper keeps on the phone, as one JSON document in app-private storage. Every field
 * has a default so that a file written by an older version still reads.
 */
@Serializable
internal data class StoredState(
    val devices: List<TvDevice> = emptyList(),
    val lastDeviceId: String? = null,
    val layouts: List<RemoteLayout> = emptyList(),
    val tokens: Map<String, String> = emptyMap(),
)

internal object StoredStateSerializer : Serializer<StoredState> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    override val defaultValue: StoredState = StoredState()

    override suspend fun readFrom(input: InputStream): StoredState =
        try {
            json.decodeFromString(StoredState.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable Zapper state", e)
        }

    override suspend fun writeTo(
        t: StoredState,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(StoredState.serializer(), t).encodeToByteArray())
    }
}
