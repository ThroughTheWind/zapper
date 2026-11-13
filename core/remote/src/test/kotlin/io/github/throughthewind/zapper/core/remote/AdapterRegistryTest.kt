package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AdapterRegistryTest {
    private class StubAdapter(
        override val id: String,
        private val found: List<DiscoveredTv> = emptyList(),
        private val answers: Set<String> = emptySet(),
        private val failDiscovery: Boolean = false,
    ) : TvAdapter {
        override val displayName = id

        override fun discover(): Flow<DiscoveredTv> =
            if (failDiscovery) flow { throw IllegalStateException("no multicast") } else flowOf(*found.toTypedArray())

        override suspend fun probe(host: String) = if (host in answers) DiscoveredTv(id, host, "$id TV") else null

        override fun createSession(
            device: TvDevice,
            credentials: CredentialStore,
        ) = RecordingSession(device)
    }

    private class RecordingSession(
        override val device: TvDevice,
    ) : TvSession {
        val sent = mutableListOf<RemoteKey>()
        override val capabilities: StateFlow<Set<Capability>> = MutableStateFlow(setOf(Capability.KEYS))
        override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)

        override suspend fun connect() = Unit

        override fun supports(key: RemoteKey) = true

        override suspend fun send(
            key: RemoteKey,
            action: KeyAction,
        ) {
            sent += key
        }

        override suspend fun disconnect() = Unit
    }

    private fun tv(
        adapter: String,
        host: String,
        uuid: String? = null,
    ) = DiscoveredTv(adapter, host, "TV $host", uniqueId = uuid)

    @Test
    fun `discovery merges adapters and emits each TV once`() =
        runTest {
            val registry =
                AdapterRegistry(
                    listOf(
                        StubAdapter("samsung", listOf(tv("samsung", "192.168.1.20", "uuid:a"), tv("samsung", "192.168.1.20", "uuid:a"))),
                        StubAdapter("lg", listOf(tv("lg", "192.168.1.30"))),
                    ),
                )

            val found = registry.discover().toList()

            assertEquals(setOf("192.168.1.20", "192.168.1.30"), found.map { it.host }.toSet())
            assertEquals(2, found.size)
        }

    @Test
    fun `one adapter failing discovery does not stop the others`() =
        runTest {
            val registry =
                AdapterRegistry(listOf(StubAdapter("broken", failDiscovery = true), StubAdapter("lg", listOf(tv("lg", "192.168.1.30")))))

            assertEquals(listOf("192.168.1.30"), registry.discover().toList().map { it.host })
        }

    @Test
    fun `probing an address asks every adapter`() =
        runTest {
            val registry = AdapterRegistry(listOf(StubAdapter("samsung"), StubAdapter("lg", answers = setOf("192.168.1.30"))))

            assertEquals("lg", registry.probe("192.168.1.30")?.adapterId)
            assertNull(registry.probe("192.168.1.99"))
        }

    @Test
    fun `two adapters cannot share an id`() {
        assertFailsWith<IllegalArgumentException> { AdapterRegistry(listOf(StubAdapter("samsung"), StubAdapter("samsung"))) }
    }

    @Test
    fun `a discovered TV becomes a device keyed on its stable id`() {
        val device = DiscoveredTv("samsung", "192.168.1.20", "Living room", macAddress = "aa:bb", uniqueId = "uuid:a").toDevice()

        assertEquals("uuid:a", device.id)
        assertEquals("samsung", device.adapterId)
    }

    @Test
    fun `a macro sends its keys in order`() =
        runTest {
            val session = RecordingSession(TvDevice("tv", "TV", "192.168.1.20", "samsung"))

            session.perform(ButtonAction.Macro(listOf(RemoteKey.DIGIT_4, RemoteKey.DIGIT_2, RemoteKey.OK)))

            assertEquals(listOf(RemoteKey.DIGIT_4, RemoteKey.DIGIT_2, RemoteKey.OK), session.sent)
        }

    @Test
    fun `launching an app on a TV that cannot fails with Unsupported`() =
        runTest {
            val session = RecordingSession(TvDevice("tv", "TV", "192.168.1.20", "samsung"))

            val error = assertFailsWith<TvException> { session.perform(ButtonAction.LaunchApp("netflix")) }
            assertEquals(TvError.Unsupported("launching apps"), error.error)
        }
}
