package io.github.throughthewind.zapper.adapters.fake

import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.CredentialStore
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.TvSessionContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeTvAdapterTest : TvSessionContract() {
    private val adapter = FakeTvAdapter(FakeTvBehaviour(discoveryDelayMs = 0, pairingDelayMs = 0))
    private val credentials = MemoryCredentials()

    override fun newSession(): TvSession =
        runBlocking { adapter.createSession(adapter.probe(FakeTvAdapter.HOST)!!.toDevice(), credentials) }

    override fun keysReceived(): List<String> = adapter.received

    @Test
    fun `discovery finds the simulated TV`() =
        runBlocking {
            assertEquals(FakeTvAdapter.HOST, adapter.discover().first().host)
        }

    @Test
    fun `probing another address finds nothing`() =
        runBlocking {
            assertNull(adapter.probe("192.168.1.20"))
        }

    @Test
    fun `pairing happens once, then the token is reused`() =
        runBlocking {
            newSession().connect()
            assertEquals("fake-token", credentials.token("uuid:fake-tv-1"))
            newSession().connect()
        }

    @Test
    fun `a refused pairing fails with PairingRefused`() =
        runBlocking {
            val refusing = FakeTvAdapter(FakeTvBehaviour(pairingDelayMs = 0, refusePairing = true))
            val session = refusing.createSession(refusing.probe(FakeTvAdapter.HOST)!!.toDevice(), MemoryCredentials())
            val error = assertFailsWith<TvException> { session.connect() }
            assertEquals(TvError.PairingRefused, error.error)
            assertTrue(session.state.value is ConnectionState.Failed)
        }

    @Test
    fun `an unsupported key is not sent`() =
        runBlocking {
            val session = newSession()
            session.connect()
            session.send(RemoteKey.GUIDE)
            assertTrue(adapter.received.isEmpty())
        }
}

internal class MemoryCredentials : CredentialStore {
    private val tokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun token(deviceId: String) = tokens[deviceId]

    override suspend fun saveToken(
        deviceId: String,
        token: String,
    ) {
        tokens[deviceId] = token
    }

    override suspend fun clear(deviceId: String) {
        tokens.remove(deviceId)
    }
}
