package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.InMemoryCredentialStore
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Trust on first use over wss://, against a fake TV with its own self-signed certificate. */
class SamsungPinningTest {
    private val credentials = InMemoryCredentialStore()
    private val servers = mutableListOf<MockWebServer>()

    @AfterEach
    fun stop() = servers.forEach { it.close() }

    private fun tvWith(certificate: HeldCertificate): MockWebServer {
        val server = MockWebServer()
        servers += server
        server.useHttps(
            HandshakeCertificates
                .Builder()
                .heldCertificate(certificate)
                .build()
                .sslSocketFactory(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .webSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            webSocket.send("""{"event":"ms.channel.connect","data":{"token":"55555"}}""")
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
        server.start()
        return server
    }

    private fun session(server: MockWebServer) =
        SamsungSession(
            device = TvDevice("tv-1", "Living room", server.hostName, SamsungAdapter.ID),
            credentials = credentials,
            client = OkHttpClient(),
            appName = "Zapper",
            scheme = "wss",
            port = server.port,
            pairingTimeoutMs = 5_000,
        )

    private fun certificate() = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()

    @Test
    fun `the first pairing pins the TV certificate`() =
        runBlocking {
            val certificate = certificate()
            assertNull(credentials.read("tv-1", "certPin"))

            val session = session(tvWith(certificate))
            session.connect()
            session.disconnect()

            assertEquals(TvTls.fingerprint(certificate.certificate), credentials.read("tv-1", "certPin"))
        }

    @Test
    fun `the same certificate is accepted again`() =
        runBlocking {
            val certificate = certificate()
            session(tvWith(certificate)).apply { connect() }.disconnect()

            val again = session(tvWith(certificate))
            again.connect()
            again.disconnect()
        }

    @Test
    fun `a different certificate after pairing fails with CertificateChanged`() =
        runBlocking {
            session(tvWith(certificate())).apply { connect() }.disconnect()
            val pinned = credentials.read("tv-1", "certPin")

            val error = assertFailsWith<TvException> { session(tvWith(certificate())).connect() }

            assertEquals(TvError.CertificateChanged, error.error)
            assertEquals(pinned, credentials.read("tv-1", "certPin"))
        }
}
