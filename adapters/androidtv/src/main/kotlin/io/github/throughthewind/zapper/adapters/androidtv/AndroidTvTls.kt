package io.github.throughthewind.zapper.adapters.androidtv

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Both ports are TLS with a client certificate. The TV's own certificate is self-signed, so it is
 * trusted on first use: pinned after the first successful connection, as Samsung's is.
 */
internal object AndroidTvTls {
    /**
     * Opens TCP to [host]:[port] within [timeoutMs], then shakes hands presenting [identity], which
     * is only made once the port answered (making one costs up to a second).
     */
    fun connect(
        host: String,
        port: Int,
        timeoutMs: Int,
        trust: PinningTrustManager,
        identity: () -> ClientIdentity,
    ): SSLSocket {
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(host, port), timeoutMs)
            raw.soTimeout = timeoutMs
        } catch (e: Exception) {
            raw.close()
            throw e
        }
        try {
            val context =
                SSLContext
                    .getInstance(
                        "TLS",
                    ).apply { init(arrayOf(ClientKeyManager(identity())), arrayOf(trust), SecureRandom()) }
            val socket = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            socket.startHandshake()
            return socket
        } catch (e: IOException) {
            raw.close()
            throw HandshakeFailed(e)
        } catch (e: Exception) {
            raw.close()
            throw e
        }
    }

    fun fingerprint(certificate: X509Certificate): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
}

/**
 * The port answered but TLS did not complete. A TV that refuses the client certificate shows up
 * here, as an alert or, as often, as a reset or broken pipe in the middle of the handshake.
 */
internal class HandshakeFailed(
    cause: IOException,
) : IOException(cause.message, cause)

/** Presents one key and certificate whatever the TV asks for. */
internal class ClientKeyManager(
    private val identity: ClientIdentity,
) : X509ExtendedKeyManager() {
    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String = ALIAS

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ): String = ALIAS

    override fun getClientAliases(
        keyType: String?,
        issuers: Array<out Principal>?,
    ): Array<String> = arrayOf(ALIAS)

    override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(identity.certificate)

    override fun getPrivateKey(alias: String?): PrivateKey = identity.privateKey

    override fun getServerAliases(
        keyType: String?,
        issuers: Array<out Principal>?,
    ): Array<String>? = null

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? = null

    private companion object {
        const val ALIAS = "zapper"
    }
}

/**
 * Accepts the TV's leaf certificate if it matches [pin], or any certificate when there is no pin
 * yet, remembering what it saw so the caller can pin it once the connection is accepted.
 */
internal class PinningTrustManager(
    private val pin: String?,
) : X509TrustManager {
    @Volatile var seen: X509Certificate? = null
        private set

    @Volatile var mismatch: Boolean = false
        private set

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("The TV sent no certificate")
        seen = leaf
        if (pin != null && pin != AndroidTvTls.fingerprint(leaf)) {
            mismatch = true
            throw CertificateException("The TV's certificate changed since pairing")
        }
    }

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ): Unit = throw CertificateException("Not a server")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
