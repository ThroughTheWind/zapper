package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.CredentialStore
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Drives [SamsungSession] against a fake TV served by MockWebServer over plain ws://. */
class SamsungSessionTest {
    private val server = MockWebServer()
    private val received = LinkedBlockingQueue<String>()
    private val credentials = InMemoryCredentials()

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.close()

    private fun fakeTv(firstMessage: String) {
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
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            received += text
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
        )

    @Test
    fun `pairing stores the token the TV issues, then keys reach the TV`() =
        runBlocking {
            fakeTv("""{"event":"ms.channel.connect","data":{"token":"55555"}}""")
            val session = session()

            session.connect()
            session.send(RemoteKey.VOLUME_UP)

            assertEquals(ConnectionState.Connected, session.state.value)
            assertEquals("55555", credentials.token("tv-1"))
            val message = received.poll(5, TimeUnit.SECONDS)
            assertEquals(SamsungProtocol.keyPress("KEY_VOLUP"), message)
            session.disconnect()
        }

    @Test
    fun `a refused prompt fails the connection`() =
        runBlocking {
            fakeTv("""{"event":"ms.channel.unauthorized"}""")
            val session = session()

            assertFailsWith<IllegalStateException> { session.connect() }
            assertEquals(true, session.state.value is ConnectionState.Failed)
        }

    private class InMemoryCredentials : CredentialStore {
        private val tokens = mutableMapOf<String, String>()

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
}
