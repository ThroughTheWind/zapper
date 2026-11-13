package io.github.throughthewind.zapper.adapters.samsung

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Samsung TVs serve a self-signed certificate on port 8002, so normal TLS validation always fails.
 * This client accepts any certificate, but only for the TV's own host on the local network.
 *
 * TODO: pin the certificate the first time the user pairs (trust on first use) instead of
 * accepting any certificate. Tracked in docs/backlog/FINDINGS.md.
 */
internal object TvTls {
    fun trustingTvCertificate(
        base: OkHttpClient,
        host: String,
    ): OkHttpClient {
        val trustAll =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        return base
            .newBuilder()
            .sslSocketFactory(context.socketFactory, trustAll)
            .hostnameVerifier { hostname, _ -> hostname == host }
            .build()
    }
}
