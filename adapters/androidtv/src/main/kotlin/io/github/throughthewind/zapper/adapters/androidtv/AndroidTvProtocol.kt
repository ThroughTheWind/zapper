package io.github.throughthewind.zapper.adapters.androidtv

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * Pairing on port 6467, Google's "Polo" protocol: every message is an `OuterMessage` (polo.proto)
 * with a protocol version, a status and one payload field (research §3).
 */
internal object Polo {
    const val PORT = 6467
    const val CODE_LENGTH = 6

    private const val PROTOCOL_VERSION = 2
    const val STATUS_OK = 200L
    const val STATUS_BAD_SECRET = 402L

    // OuterMessage payload fields.
    const val PAIRING_REQUEST = 10
    const val PAIRING_REQUEST_ACK = 11
    const val OPTIONS = 20
    const val CONFIGURATION = 30
    const val CONFIGURATION_ACK = 31
    const val SECRET = 40
    const val SECRET_ACK = 41

    private const val ENCODING_HEXADECIMAL = 3
    private const val ROLE_INPUT = 1

    /** The service name every client sends; the TV shows [clientName] in its list of remotes. */
    fun pairingRequest(clientName: String) =
        outer {
            message(PAIRING_REQUEST) {
                string(1, "atvremote")
                string(2, clientName)
            }
        }

    /** The phone takes input: the TV shows a 6-character hex code and the user types it. */
    fun options() =
        outer {
            message(OPTIONS) {
                message(1) { hexEncoding() }
                int(3, ROLE_INPUT)
            }
        }

    fun configuration() =
        outer {
            message(CONFIGURATION) {
                message(1) { hexEncoding() }
                int(2, ROLE_INPUT)
            }
        }

    fun secret(secret: ByteArray) = outer { message(SECRET) { bytes(1, secret) } }

    /**
     * The secret for [code]: SHA-256 over both public keys and the code's last four characters.
     * The code's first byte repeats the hash's first byte, so a typo is caught here, before
     * anything is sent; null then.
     */
    fun secretFor(
        client: RSAPublicKey,
        server: RSAPublicKey,
        code: String,
    ): ByteArray? {
        val normalized = code.trim().lowercase()
        if (normalized.length != CODE_LENGTH || normalized.any { it !in HEX }) return null
        val digest =
            MessageDigest.getInstance("SHA-256").run {
                update(unsigned(client.modulus))
                update(unsigned(client.publicExponent))
                update(unsigned(server.modulus))
                update(unsigned(server.publicExponent))
                update(hex(normalized.substring(2)))
                digest()
            }
        return if ((digest[0].toInt() and 0xFF) == normalized.substring(0, 2).toInt(16)) digest else null
    }

    /** Big-endian bytes without the sign byte `BigInteger` adds, as the reference hashes them. */
    fun unsigned(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private const val HEX = "0123456789abcdef"

    private fun ProtoWriter.hexEncoding() {
        int(1, ENCODING_HEXADECIMAL)
        int(2, CODE_LENGTH)
    }

    private fun outer(build: ProtoWriter.() -> Unit): ByteArray =
        ProtoWriter()
            .int(1, PROTOCOL_VERSION)
            .int(2, STATUS_OK)
            .apply(build)
            .toByteArray()
}

/** Control on port 6466: a stream of `RemoteMessage` (remotemessage.proto, research §3). */
internal object Remote {
    const val PORT = 6466

    // RemoteMessage fields.
    const val CONFIGURE = 1
    const val SET_ACTIVE = 2
    const val ERROR = 3
    const val PING_REQUEST = 8
    const val PING_RESPONSE = 9
    const val KEY_INJECT = 10
    const val IME_KEY_INJECT = 20
    const val IME_BATCH_EDIT = 21
    const val IME_SHOW_REQUEST = 22
    const val START = 40
    const val SET_VOLUME_LEVEL = 50
    const val APP_LINK_LAUNCH = 90

    // RemoteDirection.
    const val START_LONG = 1
    const val END_LONG = 2
    const val SHORT = 3

    /** Feature bits a client asks for in `RemoteConfigure.code1`, as the reference does. */
    object Feature {
        const val PING = 1
        const val KEY = 2
        const val IME = 4
        const val POWER = 32
        const val VOLUME = 64
        const val APP_LINK = 512
    }

    const val WANTED_FEATURES = Feature.PING or Feature.KEY or Feature.IME or Feature.POWER or Feature.VOLUME or Feature.APP_LINK

    /** Answers the TV's `RemoteConfigure` with the features both sides have. */
    fun configure(features: Int) =
        ProtoWriter()
            .message(CONFIGURE) {
                int(1, features)
                message(2) {
                    int(3, 1)
                    string(4, "1")
                    string(5, "atvremote")
                    string(6, "1.0.0")
                }
            }.toByteArray()

    fun setActive(features: Int) = ProtoWriter().message(SET_ACTIVE) { int(1, features) }.toByteArray()

    fun pingResponse(value: Long) = ProtoWriter().message(PING_RESPONSE) { int(1, value) }.toByteArray()

    fun key(
        code: Int,
        direction: Int,
    ) = ProtoWriter()
        .message(KEY_INJECT) {
            int(1, code)
            int(2, direction)
        }.toByteArray()

    /** Inserts [text] into the focused field, cursor after it, as the reference's `send_text`. */
    fun imeText(
        imeCounter: Long,
        fieldCounter: Long,
        text: String,
    ) = ProtoWriter()
        .message(IME_BATCH_EDIT) {
            int(1, imeCounter)
            int(2, fieldCounter)
            message(3) {
                int(1, 1)
                message(2) {
                    int(1, text.length)
                    int(2, text.length)
                    string(3, text)
                }
            }
        }.toByteArray()

    fun appLink(link: String) = ProtoWriter().message(APP_LINK_LAUNCH) { string(1, link) }.toByteArray()
}
