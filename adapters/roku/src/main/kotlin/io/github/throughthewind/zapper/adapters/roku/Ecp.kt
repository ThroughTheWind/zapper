package io.github.throughthewind.zapper.adapters.roku

import io.github.throughthewind.zapper.core.model.decodeHtmlEntities
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Roku's External Control Protocol: plain HTTP on port 8060, no pairing (research §5). Commands
 * are `POST` with an empty body, queries are `GET` and answer XML.
 */
internal class Ecp(
    private val client: OkHttpClient,
    private val host: String,
    private val port: Int = PORT,
) {
    /**
     * `GET /query/<what>`. Throws [TvException] with [TvError.ControlDisabled] on 403 and
     * [TvError.Unreachable] when the Roku does not answer, and [EcpStatusException] for any other
     * error status, such as 404 for an app that is not installed.
     */
    suspend fun query(vararg path: String): String = call("GET", listOf("query") + path)

    /** `POST /<command>/<argument>`, for example `keypress/Home`. */
    suspend fun command(
        command: String,
        argument: String,
    ) {
        call("POST", listOf(command, argument))
    }

    private suspend fun call(
        method: String,
        encodedPath: List<String>,
    ): String =
        withContext(Dispatchers.IO) {
            val url =
                HttpUrl
                    .Builder()
                    .scheme("http")
                    .host(host)
                    .port(port)
                    .apply { encodedPath.forEach(::addEncodedPathSegment) }
                    .build()
            val body = if (method == "POST") ByteArray(0).toRequestBody() else null
            try {
                client
                    .newCall(
                        Request
                            .Builder()
                            .url(url)
                            .method(method, body)
                            .build(),
                    ).execute()
                    .use { response ->
                        when {
                            response.code == FORBIDDEN -> throw TvException(TvError.ControlDisabled(CONTROL_DISABLED_HELP))
                            !response.isSuccessful -> throw EcpStatusException(response.code)
                            else -> response.body.string()
                        }
                    }
            } catch (e: IOException) {
                throw TvException(TvError.Unreachable(), e)
            }
        }

    companion object {
        const val PORT = 8060
        private const val FORBIDDEN = 403

        /** What the 403 means since Roku OS 10, and where to change it (research §5). */
        const val CONTROL_DISABLED_HELP =
            "This Roku blocks remotes on phones. On the Roku, open Settings › System › Advanced system settings › " +
                "Control by mobile apps › Network access and choose Default, then try again."

        /**
         * One path segment, percent-encoded byte by byte in UTF-8, leaving only RFC 3986 unreserved
         * characters as they are. `Lit_` text needs this: a raw `+`, `/` or `?` would change the URL.
         */
        fun encodeSegment(text: String): String =
            buildString {
                text.toByteArray(Charsets.UTF_8).forEach { byte ->
                    val c = (byte.toInt() and 0xFF).toChar()
                    if ((c.isLetterOrDigit() && c.code < 0x80) || c in "-._~") append(c) else append("%%%02X".format(byte.toInt() and 0xFF))
                }
            }

        /** The text of each flat `<tag>value</tag>` in [xml], entities decoded. Enough for ECP's answers. */
        fun fields(xml: String): Map<String, String> =
            FIELD
                .findAll(xml)
                .associate { it.groupValues[1] to it.groupValues[2].trim().decodeHtmlEntities() }

        private val FIELD = Regex("<([a-zA-Z0-9-]+)(?:\\s[^>]*)?>([^<]*)</\\1>")
    }
}

/** The Roku answered, with an error status other than 403. */
internal class EcpStatusException(
    val code: Int,
) : Exception("The Roku answered $code")
