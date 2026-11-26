package io.github.throughthewind.zapper.adapters.androidtv

import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.InMemoryCredentialStore
import io.github.throughthewind.zapper.core.remote.PairingRequest
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.TvSessionContract
import io.github.throughthewind.zapper.core.remote.saveToken
import io.github.throughthewind.zapper.core.remote.token
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The session against [FakeAndroidTv] over real TLS sockets on localhost. */
class AndroidTvSessionTest : TvSessionContract() {
    private val tv = FakeAndroidTv()
    private val adapter = AndroidTvAdapter("Zapper test", { emptyFlow() }, tv.pairingPort, tv.remotePort, wake = { _, _ -> })
    private val credentials = InMemoryCredentialStore()
    private val device: TvDevice by lazy { runBlocking { assertNotNull(adapter.probe(FakeAndroidTv.HOST)).toDevice() } }

    @AfterEach
    fun stopTv() = tv.close()

    /** A session whose phone the TV already paired with, as the contract asks. */
    override fun newSession(): TvSession {
        tv.pair(PAIRED)
        runBlocking { credentials.saveToken(device.id, PAIRED.encode()) }
        return adapter.createSession(device, credentials)
    }

    override fun keysReceived(): List<String> = tv.keys

    @Test
    fun `probing reads the name and MAC from the TV's certificate`() =
        runBlocking {
            assertEquals("Fake TV", device.name)
            assertEquals("Fake TV", device.model)
            assertEquals("aa:bb:cc:dd:ee:ff", device.macAddress)
            assertEquals("aa:bb:cc:dd:ee:ff", device.id)
            assertTrue(adapter.canPowerOn(device))
        }

    @Test
    fun `the power-on help is translated into every language the app ships`() {
        assertEquals(setOf("fr", "es", "de", "pl"), adapter.powerOnHelp.translations.keys)
        adapter.powerOnHelp.translations.values
            .forEach { assertTrue(it.isNotBlank() && it != adapter.powerOnHelp.english) }
    }

    @Test
    fun `probing an address without the service finds nothing`() =
        runBlocking {
            // A port nothing listens on. Closing the fake TV is not enough: its listening socket stays
            // open until its accept thread wakes, which on a busy machine is after the probe.
            val unused = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
            val elsewhere = AndroidTvAdapter("Zapper test", { emptyFlow() }, unused, unused, wake = { _, _ -> })
            assertNull(elsewhere.probe(FakeAndroidTv.HOST))
        }

    @Test
    fun `pairing takes the code once, then the certificate is reused`() =
        runBlocking {
            val session = adapter.createSession(device, credentials)
            val connecting = async { session.connect() }
            val request = awaitCode(session)
            val code = assertNotNull(tv.code)

            // A typo is caught before anything reaches the TV, and the same code can be typed again.
            val typo = (if (code[0] == '0') "1" else "0") + code.substring(1)
            val error = assertFailsWith<TvException> { request.submit(typo) }
            assertEquals(TvError.PairingRefused, error.error)

            request.submit(code.uppercase())
            withTimeout(10_000) { connecting.await() }
            assertEquals(ConnectionState.Connected, session.state.value)
            assertNotNull(credentials.token(device.id))
            session.disconnect()

            val again = adapter.createSession(device, credentials)
            again.connect()
            assertEquals(ConnectionState.Connected, again.state.value)
            assertEquals(1, tv.pairings)
            again.disconnect()
        }

    @Test
    fun `a pairing the TV refuses fails with PairingRefused`() =
        runBlocking {
            tv.refuse = true
            val session = adapter.createSession(device, credentials)
            val error = assertFailsWith<TvException> { session.connect() }
            assertEquals(TvError.PairingRefused, error.error)
            assertEquals(ConnectionState.Failed(TvError.PairingRefused), session.state.value)
        }

    @Test
    fun `a code the TV rejects fails with PairingRefused`() =
        runBlocking {
            tv.rejectSecret = true
            val session = adapter.createSession(device, credentials)
            val connecting = async { runCatching { session.connect() } }
            val request = awaitCode(session)
            assertFailsWith<TvException> { request.submit(assertNotNull(tv.code)) }
            assertEquals(TvError.PairingRefused, (connecting.await().exceptionOrNull() as TvException).error)
            assertNull(credentials.token(device.id))
        }

    @Test
    fun `a certificate the TV forgot fails with TokenRejected`() =
        runBlocking {
            credentials.saveToken(device.id, PAIRED.encode())
            val error = assertFailsWith<TvException> { adapter.createSession(device, credentials).connect() }
            assertEquals(TvError.TokenRejected, error.error)
        }

    @Test
    fun `a TV whose certificate changed fails with CertificateChanged`() =
        runBlocking {
            val session = newSession()
            session.connect()
            session.disconnect()
            credentials.write(device.id, "certPin", "0".repeat(64))
            val error = assertFailsWith<TvException> { adapter.createSession(device, credentials).connect() }
            assertEquals(TvError.CertificateChanged, error.error)
        }

    @Test
    fun `the TV's ping is answered`() =
        runBlocking {
            val session = newSession()
            session.connect()
            assertEquals(listOf(7L), tv.pings.toList())
            session.disconnect()
        }

    @Test
    fun `a long press starts and ends`() =
        runBlocking {
            val session = newSession()
            session.connect()
            session.send(RemoteKey.VOLUME_UP, KeyAction.PRESS)
            session.send(RemoteKey.VOLUME_UP, KeyAction.RELEASE)
            session.send(RemoteKey.OK)
            awaitSize(tv.keys, 3)
            assertEquals(listOf("24:1", "24:2", "23:3"), tv.keys.toList())
            session.disconnect()
        }

    @Test
    fun `text goes to the focused field, then Enter`() =
        runBlocking {
            val session = newSession()
            session.connect()
            session.sendText("Severance")
            awaitSize(tv.keys, 1)
            assertEquals(listOf("5:Severance"), tv.texts.toList())
            assertEquals(listOf("66:3"), tv.keys.toList())
            session.disconnect()
        }

    @Test
    fun `apps open by deep link`() =
        runBlocking {
            val session = newSession()
            session.connect()
            session.launchApp("youtube")
            session.launchApp("com.example.tv")
            awaitSize(tv.links, 2)
            assertEquals(listOf("https://www.youtube.com", "market://launch?id=com.example.tv"), tv.links.toList())
            session.disconnect()
        }

    @Test
    fun `keys without a confirmed code are not supported`() {
        val session = adapter.createSession(device, credentials)
        assertTrue(session.supports(RemoteKey.HOME))
        assertTrue(!session.supports(RemoteKey.HDMI_1))
        assertTrue(!session.supports(RemoteKey.EXIT))
    }

    @Test
    fun `a TV that drops the connection ends in ConnectionLost`() =
        runBlocking {
            val session = newSession()
            session.connect()
            tv.dropClients()
            withTimeout(5_000) { session.state.first { it is ConnectionState.Failed } }
            assertEquals(ConnectionState.Failed(TvError.ConnectionLost()), session.state.value)
            assertFailsWith<TvException> { session.send(RemoteKey.OK) }
        }

    private suspend fun awaitCode(session: TvSession): PairingRequest.EnterCode =
        withTimeout(10_000) {
            session.state
                .filterIsInstance<ConnectionState.AwaitingPairing>()
                .first()
                .request as PairingRequest.EnterCode
        }

    private fun awaitSize(
        list: List<*>,
        size: Int,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        while (list.size < size && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    private companion object {
        val PAIRED: ClientIdentity by lazy { ClientIdentity.generate("paired phone") }
    }
}
