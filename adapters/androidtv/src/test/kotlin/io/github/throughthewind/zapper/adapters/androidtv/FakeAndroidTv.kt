package io.github.throughthewind.zapper.adapters.androidtv

import java.io.Closeable
import java.net.InetAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Collections
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * An Android TV on localhost speaking the real protocol: TLS with client certificates on both
 * ports, the Polo pairing exchange with a code, and the remote handshake with a ping. It lets in on
 * the remote port only the client certificates it has paired with.
 */
internal class FakeAndroidTv(
    subject: String = "atvremote/fake/fake/Fake TV/AA:BB:CC:DD:EE:FF",
) : Closeable {
    val keys = CopyOnWriteArrayList<String>()
    val texts = CopyOnWriteArrayList<String>()
    val links = CopyOnWriteArrayList<String>()
    val pings = CopyOnWriteArrayList<Long>()

    /** Fingerprints of the client certificates the TV accepts on the remote port. */
    val paired: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    @Volatile var pairings = 0

    @Volatile var refuse = false

    /** Answers even the right code with STATUS_BAD_SECRET. */
    @Volatile var rejectSecret = false

    private val remoteClients = CopyOnWriteArrayList<SSLSocket>()

    /** The code on the TV's screen during the current pairing. */
    @Volatile var code: String? = null

    private val identity = serverIdentity(subject)
    val certificate: X509Certificate get() = identity.certificate

    private val pairingServer = server(trustOnlyPaired = false)
    private val remoteServer = server(trustOnlyPaired = true)
    val pairingPort: Int = pairingServer.localPort
    val remotePort: Int = remoteServer.localPort

    init {
        accept(pairingServer, ::pairing)
        accept(remoteServer, ::remote)
    }

    fun pair(identity: ClientIdentity) {
        paired += AndroidTvTls.fingerprint(identity.certificate)
    }

    private fun pairing(socket: SSLSocket) {
        val client = socket.session.peerCertificates.first() as X509Certificate
        val input = socket.inputStream
        val output = socket.outputStream

        fun reply(
            status: Long = Polo.STATUS_OK,
            build: ProtoWriter.() -> Unit = {},
        ) = output.write(
            ProtoFrames.frame(
                ProtoWriter()
                    .int(1, 2)
                    .int(2, status)
                    .apply(build)
                    .toByteArray(),
            ),
        )

        while (true) {
            val message = ProtoFrames.read(input)
            when {
                message.has(Polo.PAIRING_REQUEST) -> {
                    if (refuse) return reply(status = 400)
                    reply { message(Polo.PAIRING_REQUEST_ACK) { string(1, "Fake TV") } }
                }

                message.has(Polo.OPTIONS) -> {
                    reply { message(Polo.OPTIONS) { int(3, 1) } }
                }

                message.has(Polo.CONFIGURATION) -> {
                    pairings++
                    code = codeFor(client.publicKey as RSAPublicKey)
                    reply { message(Polo.CONFIGURATION_ACK) {} }
                }

                message.has(Polo.SECRET) -> {
                    val secret = message.message(Polo.SECRET)!!.bytes(1)
                    val expected = Polo.secretFor(client.publicKey as RSAPublicKey, identity.publicKey, code!!)
                    if (rejectSecret || !secret.contentEquals(expected)) return reply(status = Polo.STATUS_BAD_SECRET)
                    paired += AndroidTvTls.fingerprint(client)
                    reply { message(Polo.SECRET_ACK) { bytes(1, secret) } }
                }
            }
        }
    }

    /** A code whose first byte checks out against the hash, as a real TV's does. */
    private fun codeFor(client: RSAPublicKey): String =
        (0..255).map { "%02x".format(it) + "a1b2" }.first { Polo.secretFor(client, identity.publicKey, it) != null }

    /** Closes every open remote connection, as a TV going to sleep would. */
    fun dropClients() = remoteClients.forEach { runCatching { it.close() } }

    private fun remote(socket: SSLSocket) {
        remoteClients += socket
        val input = socket.inputStream
        val output = socket.outputStream

        fun send(build: ProtoWriter.() -> Unit) = output.write(ProtoFrames.frame(ProtoWriter().apply(build).toByteArray()))

        send { message(Remote.CONFIGURE) { int(1, 0x27F) } }
        check(ProtoFrames.read(input).has(Remote.CONFIGURE))
        send { message(Remote.SET_ACTIVE) { int(1, 0x27F) } }
        check(ProtoFrames.read(input).has(Remote.SET_ACTIVE))
        send { message(Remote.PING_REQUEST) { int(1, 7) } }
        pings += ProtoFrames.read(input).message(Remote.PING_RESPONSE)!!.int(1)
        send { message(Remote.IME_KEY_INJECT) { message(1) { int(1, 5) } } }
        send { message(Remote.START) { int(1, 1) } }
        while (true) {
            val message = ProtoFrames.read(input)
            when {
                message.has(Remote.KEY_INJECT) -> {
                    val key = message.message(Remote.KEY_INJECT)!!
                    keys += "${key.int(1)}:${key.int(2)}"
                }

                message.has(Remote.IME_BATCH_EDIT) -> {
                    val edit = message.message(Remote.IME_BATCH_EDIT)!!
                    texts += "${edit.int(1)}:" + edit.message(3)!!.message(2)!!.string(3)
                }

                message.has(Remote.APP_LINK_LAUNCH) -> {
                    links += message.message(Remote.APP_LINK_LAUNCH)!!.string(1)
                }
            }
        }
    }

    private fun server(trustOnlyPaired: Boolean): SSLServerSocket {
        val store =
            KeyStore.getInstance("PKCS12").apply {
                load(null)
                setKeyEntry("tv", identity.privateKey, PASSWORD, arrayOf(identity.certificate))
            }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, PASSWORD) }.keyManagers
        val trust =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                    if (trustOnlyPaired && AndroidTvTls.fingerprint(chain.first()) !in paired) {
                        throw CertificateException("Not paired")
                    }
                }

                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val context = SSLContext.getInstance("TLS").apply { init(keyManagers, arrayOf(trust), null) }
        return (context.serverSocketFactory.createServerSocket(0, 10, InetAddress.getLoopbackAddress()) as SSLServerSocket).apply {
            needClientAuth = true
        }
    }

    private fun accept(
        server: SSLServerSocket,
        handler: (SSLSocket) -> Unit,
    ) = thread(isDaemon = true) {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() as SSLSocket }.getOrNull() ?: break
            thread(isDaemon = true) { (socket as Socket).use { runCatching { handler(socket) } } }
        }
    }

    override fun close() {
        pairingServer.close()
        remoteServer.close()
    }

    companion object {
        const val HOST = "127.0.0.1"
        private val PASSWORD = "fake".toCharArray()

        fun serverIdentity(subject: String): ClientIdentity {
            val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val der = Der.selfSignedCertificate(subject, keys.public.encoded, keys.private, Date(now - 60_000), Date(now + 86_400_000))
            return ClientIdentity(keys.private, ClientIdentity.certificate(der))
        }
    }
}
