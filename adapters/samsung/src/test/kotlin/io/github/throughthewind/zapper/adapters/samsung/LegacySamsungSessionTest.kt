package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.InMemoryCredentialStore
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.TvSessionContract
import io.github.throughthewind.zapper.core.remote.saveToken
import io.github.throughthewind.zapper.core.remote.token
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drives [LegacySamsungSession] against a fake 2008–2013 TV on localhost. */
class LegacySamsungSessionTest : TvSessionContract() {
    private val credentials = InMemoryCredentialStore()
    private var tv = FakeLegacyTv()

    @AfterEach
    fun stop() = tv.close()

    private fun device(port: Int = tv.port) =
        TvDevice(
            "tv-1",
            "Samsung TV",
            "127.0.0.1",
            SamsungAdapter.ID,
            properties = mapOf(SamsungAdapter.PROPERTY_PROTOCOL to SamsungAdapter.PROTOCOL_LEGACY),
        ).let { LegacySamsungSession(it, credentials, "Zapper", port, pairingTimeoutMs = 5_000) }

    override fun newSession(): TvSession = device()

    override fun keysReceived(): List<String> = tv.keys

    private fun awaitKeys(count: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (tv.keys.size < count && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    @Test
    fun `pairing once saves the id, and the next connection sends the same one`() =
        runBlocking {
            tv.close()
            tv = FakeLegacyTv { listOf(FakeLegacyTv.WAITING, FakeLegacyTv.GRANTED) }
            val first = device()
            val awaiting = async { first.state.first { it is ConnectionState.AwaitingPairing } }
            first.connect()
            withTimeout(5_000) { awaiting.await() }
            first.disconnect()

            val saved = credentials.token("tv-1")
            assertEquals(listOf(saved), tv.remoteIds.toList())
            assertEquals("Zapper", tv.names.single())

            device().connect()
            assertEquals(listOf(saved, saved), tv.remoteIds.toList())
        }

    @Test
    fun `a refused prompt is PairingRefused and saves nothing`() =
        runBlocking {
            tv.close()
            tv = FakeLegacyTv { listOf(FakeLegacyTv.WAITING, FakeLegacyTv.DENIED) }
            val session = device()

            val error = assertFailsWith<TvException> { session.connect() }

            assertEquals(TvError.PairingRefused, error.error)
            assertEquals(ConnectionState.Failed(TvError.PairingRefused), session.state.value)
            assertNull(credentials.token("tv-1"))
        }

    @Test
    fun `a saved id the TV now denies is TokenRejected`() =
        runBlocking {
            credentials.saveToken("tv-1", "removed-in-tv-settings")
            tv.close()
            tv = FakeLegacyTv { id -> listOf(if (id == "removed-in-tv-settings") FakeLegacyTv.DENIED else FakeLegacyTv.GRANTED) }

            val error = assertFailsWith<TvException> { device().connect() }

            assertEquals(TvError.TokenRejected, error.error)
        }

    @Test
    fun `an unanswered prompt is PairingTimedOut`() =
        runBlocking {
            tv.close()
            tv = FakeLegacyTv { listOf(FakeLegacyTv.WAITING, FakeLegacyTv.CANCELLED) }

            assertEquals(TvError.PairingTimedOut, assertFailsWith<TvException> { device().connect() }.error)
        }

    @Test
    fun `a TV that closes the handshake without answering is not supported`() =
        runBlocking {
            val silent = ServerSocket(0)
            Thread { runCatching { silent.accept().use { it.getInputStream().read(ByteArray(64)) } } }.start()

            val error = assertFailsWith<TvException> { device(silent.localPort).connect() }

            assertIs<TvError.Unsupported>(error.error)
            silent.close()
        }

    @Test
    fun `nothing listening is Unreachable`() =
        runBlocking {
            val port = ServerSocket(0).use { it.localPort }
            assertIs<TvError.Unreachable>(assertFailsWith<TvException> { device(port).connect() }.error)
        }

    @Test
    fun `keys use the legacy codes and a release sends nothing`() =
        runBlocking {
            val session = device()
            session.connect()

            session.send(RemoteKey.POWER)
            session.send(RemoteKey.HOME, KeyAction.PRESS)
            session.send(RemoteKey.HOME, KeyAction.RELEASE)
            session.send(RemoteKey.OK)
            awaitKeys(3)

            assertEquals(listOf("KEY_POWEROFF", "KEY_CONTENTS", "KEY_ENTER"), tv.keys.toList())
            assertFalse(session.supports(RemoteKey.PLAY_PAUSE))
            assertTrue(session.supports(RemoteKey.CHANNEL_UP))
            session.disconnect()
        }

    @Test
    fun `a TV that drops the connection leaves the session failed`() =
        runBlocking {
            val session = device()
            session.connect()

            tv.dropClients()

            withTimeout(5_000) { session.state.first { it is ConnectionState.Failed } }
            assertEquals(ConnectionState.Failed(TvError.ConnectionLost()), session.state.value)
        }
}
