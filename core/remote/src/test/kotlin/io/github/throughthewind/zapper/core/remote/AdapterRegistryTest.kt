package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AdapterRegistryTest {
    private class FakeAdapter(
        override val id: String,
        private val manufacturer: String,
    ) : TvAdapter {
        override val displayName = id

        override fun recognise(candidate: DiscoveredTv) =
            if (candidate.manufacturer == manufacturer) TvDevice(candidate.host, candidate.name ?: id, candidate.host, id) else null

        override fun createSession(
            device: TvDevice,
            credentials: CredentialStore,
        ) = FakeSession(device)
    }

    private class FakeSession(
        override val device: TvDevice,
    ) : TvSession {
        val sent = mutableListOf<RemoteKey>()
        override val capabilities = setOf(Capability.KEYS)
        override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)

        override suspend fun connect() = Unit

        override suspend fun send(key: RemoteKey) {
            sent += key
        }

        override suspend fun disconnect() = Unit
    }

    private val registry = AdapterRegistry(listOf(FakeAdapter("samsung", "Samsung"), FakeAdapter("lg", "LG")))

    @Test
    fun `the adapter whose brand matches claims the TV`() {
        val device = registry.recognise(DiscoveredTv(host = "192.168.1.20", manufacturer = "LG"))

        assertEquals("lg", device?.adapterId)
    }

    @Test
    fun `an unknown brand is claimed by nobody`() {
        assertNull(registry.recognise(DiscoveredTv(host = "192.168.1.21", manufacturer = "Acme")))
    }

    @Test
    fun `two adapters cannot share an id`() {
        assertFailsWith<IllegalArgumentException> {
            AdapterRegistry(listOf(FakeAdapter("samsung", "a"), FakeAdapter("samsung", "b")))
        }
    }

    @Test
    fun `a macro sends its keys in order`() =
        runTest {
            val session = FakeSession(TvDevice("tv", "TV", "192.168.1.20", "samsung"))

            session.perform(ButtonAction.Macro(listOf(RemoteKey.DIGIT_4, RemoteKey.DIGIT_2, RemoteKey.OK)))

            assertEquals(listOf(RemoteKey.DIGIT_4, RemoteKey.DIGIT_2, RemoteKey.OK), session.sent)
        }

    @Test
    fun `launching an app on a TV that cannot fails loudly`() =
        runTest {
            val session = FakeSession(TvDevice("tv", "TV", "192.168.1.20", "samsung"))

            assertFailsWith<UnsupportedOperationException> { session.perform(ButtonAction.LaunchApp("netflix")) }
        }
}
