package io.github.throughthewind.zapper.adapters.samsung

import java.io.EOFException
import java.io.InputStream
import java.util.Base64

/**
 * The remote protocol of Samsung TVs from 2008 to 2013 (series A to F, before Tizen): length-framed
 * packets on TCP port 55000, with base64 strings inside. No I/O here, so it can be tested without a
 * TV. See docs/research/tv-remote-protocols.md §2.9.
 *
 * Every packet is `[type: 1 byte][app: string][payload: string]`, where a string is a 16-bit
 * little-endian length followed by its bytes.
 */
internal object LegacyProtocol {
    const val PORT = 55000

    /** The app name every open implementation sends; the TV does not show it. */
    private const val APP = "iphone..iapp.samsung"

    /**
     * Asks the TV to accept this phone. [remoteId] is how the TV remembers it, so the prompt only
     * shows the first time; [name] is what the prompt shows; [phoneIp] is the phone's own address.
     */
    fun authenticate(
        phoneIp: String,
        remoteId: String,
        name: String,
    ): ByteArray =
        packet(
            byteArrayOf(0x64, 0x00) +
                string(base64(phoneIp)) +
                string(base64(remoteId)) +
                string(base64(name)),
        )

    /** One key press. The protocol has no press-and-hold: every key is a click. */
    fun key(code: String): ByteArray = packet(byteArrayOf(0x00, 0x00, 0x00) + string(base64(code)))

    private fun packet(payload: ByteArray): ByteArray = byteArrayOf(0x00) + string(APP.toByteArray(Charsets.US_ASCII)) + string(payload)

    private fun base64(text: String): ByteArray = Base64.getEncoder().encode(text.toByteArray(Charsets.UTF_8))

    private fun string(bytes: ByteArray): ByteArray {
        require(bytes.size <= 0xFFFF) { "A legacy Samsung string is at most 65535 bytes" }
        return byteArrayOf((bytes.size and 0xFF).toByte(), (bytes.size shr 8).toByte()) + bytes
    }

    /** What the TV said, from a packet's payload. */
    sealed interface Reply {
        /** The TV shows its "Allow?" prompt and waits for an answer. */
        data object Waiting : Reply

        data object Granted : Reply

        data object Denied : Reply

        /** Nobody answered the prompt, or it was dismissed. */
        data object Cancelled : Reply

        /** A key was accepted. */
        data object KeyAccepted : Reply

        data class Other(
            val payload: ByteArray,
        ) : Reply {
            override fun equals(other: Any?) = other is Other && payload.contentEquals(other.payload)

            override fun hashCode() = payload.contentHashCode()
        }
    }

    fun reply(payload: ByteArray): Reply =
        when {
            payload.contentEquals(GRANTED) -> Reply.Granted
            payload.contentEquals(DENIED) -> Reply.Denied
            payload.contentEquals(KEY_ACCEPTED) -> Reply.KeyAccepted
            payload.firstOrNull() == WAITING -> Reply.Waiting
            payload.firstOrNull() == CANCELLED -> Reply.Cancelled
            else -> Reply.Other(payload)
        }

    /** Reads one packet from [input] and returns its payload. Throws [EOFException] when the TV closed the connection. */
    fun readPayload(input: InputStream): ByteArray {
        readExactly(input, 1)
        readExactly(input, readLength(input))
        return readExactly(input, readLength(input))
    }

    private fun readLength(input: InputStream): Int {
        val bytes = readExactly(input, 2)
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }

    private fun readExactly(
        input: InputStream,
        count: Int,
    ): ByteArray {
        val bytes = input.readNBytes(count)
        if (bytes.size < count) throw EOFException("The TV closed the connection")
        return bytes
    }

    private val GRANTED = byteArrayOf(0x64, 0x00, 0x01, 0x00)
    private val DENIED = byteArrayOf(0x64, 0x00, 0x00, 0x00)
    private val KEY_ACCEPTED = byteArrayOf(0x00, 0x00, 0x00, 0x00)
    private const val WAITING: Byte = 0x0a
    private const val CANCELLED: Byte = 0x65
}
