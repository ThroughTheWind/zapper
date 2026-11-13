package io.github.throughthewind.zapper.adapters.samsung

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Samsung TVs serve a self-signed certificate on port 8002, so normal TLS validation always fails.
 * Instead the TV's certificate is trusted on first use: the first pairing records its SHA-256, and
 * every later connection must present the same one (closes finding F-001).
 */
internal object TvTls {
    fun pinning(
        base: OkHttpClient,
        host: String,
        trust: PinningTrustManager,
    ): OkHttpClient {
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        return base
            .newBuilder()
            .sslSocketFactory(context.socketFactory, trust)
            .hostnameVerifier { hostname, _ -> hostname == host }
            .build()
    }

    fun fingerprint(certificate: X509Certificate): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
}

/**
 * Accepts the TV's leaf certificate if it matches [pin], or any certificate when there is no pin
 * yet, remembering what it saw in [seen] so the caller can store it after pairing succeeds.
 */
internal class PinningTrustManager(
    private val pin: String?,
) : X509TrustManager {
    @Volatile var seen: String? = null
        private set

    @Volatile var mismatch: Boolean = false
        private set

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("The TV sent no certificate")
        val fingerprint = TvTls.fingerprint(leaf)
        seen = fingerprint
        if (pin != null && pin != fingerprint) {
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
