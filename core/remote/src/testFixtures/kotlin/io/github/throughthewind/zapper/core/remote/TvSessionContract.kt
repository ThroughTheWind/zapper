package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.RemoteKey
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behaviour every adapter's session must share, so the UI can rely on it whatever the brand.
 * An adapter's test extends this with a fake TV that accepts pairing and records keys.
 */
abstract class TvSessionContract {
    /** A fresh session to a fake TV that accepts pairing. */
    protected abstract fun newSession(): TvSession

    /** Keys the fake TV received, in order. */
    protected abstract fun keysReceived(): List<String>

    @Test
    fun `connecting reaches Connected`() =
        runBlocking {
            val session = newSession()
            session.connect()
            assertEquals(ConnectionState.Connected, session.state.value)
            session.disconnect()
        }

    @Test
    fun `every session can send keys`() {
        assertTrue(Capability.KEYS in newSession().capabilities.value)
    }

    @Test
    fun `sending before connecting fails with NotConnected`() =
        runBlocking {
            val error = assertFailsWith<TvException> { newSession().send(RemoteKey.OK) }
            assertEquals(TvError.NotConnected, error.error)
        }

    @Test
    fun `a supported key reaches the TV`() =
        runBlocking {
            val session = newSession()
            session.connect()
            assertTrue(session.supports(RemoteKey.VOLUME_UP))
            session.send(RemoteKey.VOLUME_UP)
            awaitKeys(1)
            assertEquals(1, keysReceived().size)
            session.disconnect()
        }

    @Test
    fun `disconnecting ends Disconnected`() =
        runBlocking {
            val session = newSession()
            session.connect()
            session.disconnect()
            assertEquals(ConnectionState.Disconnected, session.state.value)
        }

    private fun awaitKeys(count: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (keysReceived().size < count && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }
}
