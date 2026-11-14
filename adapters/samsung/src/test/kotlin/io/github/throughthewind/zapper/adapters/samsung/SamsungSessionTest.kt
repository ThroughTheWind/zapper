package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.InMemoryCredentialStore
import io.github.throughthewind.zapper.core.remote.TvApp
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.TvSessionContract
import io.github.throughthewind.zapper.core.remote.saveToken
import io.github.throughthewind.zapper.core.remote.token
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Drives [SamsungSession] against a fake TV served by MockWebServer over plain ws://. */
class SamsungSessionTest : TvSessionContract() {
    private val server = MockWebServer()
    private val received = LinkedBlockingQueue<String>()
    private val receivedList = CopyOnWriteArrayList<String>()
    private val credentials = InMemoryCredentialStore()

    /** What the fake TV answers to `ed.installedApp.get`; null never answers, like some firmware. */
    @Volatile private var appList: String? = null

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.close()

    private fun fakeTv(
        firstMessage: String = CONNECT_WITH_TOKEN,
        vararg then: String,
    ) {
        server.enqueue(
            MockResponse
                .Builder()
                .webSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            webSocket.send(firstMessage)
                            then.forEach { webSocket.send(it) }
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            if (text.contains("ed.installedApp.get")) {
                                appList?.let(webSocket::send)
                                return
                            }
                            received += text
                            receivedList += text
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            webSocket.close(code, null)
                        }
                    },
                ).build(),
        )
    }

    private fun session() =
        SamsungSession(
            device = TvDevice("tv-1", "Living room", server.hostName, SamsungAdapter.ID),
            credentials = credentials,
            client = OkHttpClient(),
            appName = "Zapper",
            scheme = "ws",
            port = server.port,
            pairingTimeoutMs = 5_000,
            appsTimeoutMs = 300,
        )

    override fun newSession(): TvSession {
        fakeTv()
        return session()
    }

    override fun keysReceived(): List<String> = receivedList

    @Test
    fun `pairing stores the token the TV issues, then keys reach the TV`() =
        runBlocking {
            fakeTv()
            val session = session()

            session.connect()
            session.send(RemoteKey.VOLUME_UP)

            assertEquals("55555", credentials.token("tv-1"))
            assertEquals(SamsungProtocol.keyPress("KEY_VOLUP"), received.poll(5, TimeUnit.SECONDS))
            session.disconnect()
        }

    @Test
    fun `holding a key sends Press then Release`() =
        runBlocking {
            fakeTv()
            val session = session()
            session.connect()

            session.send(RemoteKey.VOLUME_UP, KeyAction.PRESS)
            session.send(RemoteKey.VOLUME_UP, KeyAction.RELEASE)

            assertEquals(SamsungProtocol.keyPress("KEY_VOLUP", "Press"), received.poll(5, TimeUnit.SECONDS))
            assertEquals(SamsungProtocol.keyPress("KEY_VOLUP", "Release"), received.poll(5, TimeUnit.SECONDS))
            session.disconnect()
        }

    @Test
    fun `typed text reaches the TV and is confirmed`() =
        runBlocking {
            fakeTv()
            val session = session()
            session.connect()

            session.sendText("stranger")

            assertEquals(SamsungProtocol.textInput("stranger"), received.poll(5, TimeUnit.SECONDS))
            assertEquals(SamsungProtocol.textInputEnd(), received.poll(5, TimeUnit.SECONDS))
            session.disconnect()
        }

    @Test
    fun `the session follows the TV keyboard`() =
        runBlocking {
            fakeTv(CONNECT_WITH_TOKEN, """{"event":"ms.remote.imeStart"}""")
            val session = session()
            session.connect()

            withTimeout(5_000) { session.tvKeyboard.first { it } }

            session.disconnect()
            assertFalse(session.tvKeyboard.value)
        }

    @Test
    fun `a known app opens under the id the TV lists`() =
        runBlocking {
            appList = """{"event":"ed.installedApp.get","data":{"data":[{"appId":"11101200001","app_type":2,"name":"Netflix"}]}}"""
            fakeTv()
            val session = session()
            session.connect()

            session.launchApp("netflix")

            assertEquals(SamsungProtocol.launchApp("11101200001"), received.poll(5, TimeUnit.SECONDS))
            assertEquals(listOf(TvApp("11101200001", "Netflix")), session.installedApps())
            session.disconnect()
        }

    @Test
    fun `an app missing from the TV list fails with AppNotInstalled`() =
        runBlocking {
            appList = """{"event":"ed.installedApp.get","data":{"data":[{"appId":"111299001912","app_type":2,"name":"YouTube"}]}}"""
            fakeTv()
            val session = session()
            session.connect()

            val error = assertFailsWith<TvException> { session.launchApp("netflix") }

            assertEquals(TvError.AppNotInstalled, error.error)
            session.disconnect()
        }

    @Test
    fun `a TV that does not list its apps gets the newest id`() =
        runBlocking {
            fakeTv()
            val session = session()
            session.connect()

            session.launchApp("netflix")

            assertEquals(SamsungProtocol.launchApp("3201907018807"), received.poll(5, TimeUnit.SECONDS))
            session.disconnect()
        }

    @Test
    fun `a refused prompt fails with PairingRefused`() =
        runBlocking {
            fakeTv(UNAUTHORIZED)
            val session = session()

            val error = assertFailsWith<TvException> { session.connect() }
            assertEquals(TvError.PairingRefused, error.error)
            assertTrue(session.state.value is ConnectionState.Failed)
        }

    @Test
    fun `a saved token the TV no longer accepts fails with TokenRejected`() =
        runBlocking {
            credentials.saveToken("tv-1", "old")
            fakeTv(UNAUTHORIZED)

            val error = assertFailsWith<TvException> { session().connect() }
            assertEquals(TvError.TokenRejected, error.error)
        }

    @Test
    fun `a TV that is not there fails with Unreachable`() =
        runBlocking {
            val session = session()
            server.close()

            val error = assertFailsWith<TvException> { session.connect() }
            assertTrue(error.error is TvError.Unreachable)
        }

    private companion object {
        const val CONNECT_WITH_TOKEN = """{"event":"ms.channel.connect","data":{"token":"55555"}}"""
        const val UNAUTHORIZED = """{"event":"ms.channel.unauthorized"}"""
    }
}
