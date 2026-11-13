package io.github.throughthewind.zapper.core.remote

import java.util.concurrent.ConcurrentHashMap

/** A [CredentialStore] for tests. */
class InMemoryCredentialStore : CredentialStore {
    private val secrets = ConcurrentHashMap<Pair<String, String>, String>()

    override suspend fun read(
        deviceId: String,
        name: String,
    ): String? = secrets[deviceId to name]

    override suspend fun write(
        deviceId: String,
        name: String,
        value: String,
    ) {
        secrets[deviceId to name] = value
    }

    override suspend fun remove(
        deviceId: String,
        name: String,
    ) {
        secrets.remove(deviceId to name)
    }

    override suspend fun clear(deviceId: String) {
        secrets.keys.removeIf { it.first == deviceId }
    }
}
