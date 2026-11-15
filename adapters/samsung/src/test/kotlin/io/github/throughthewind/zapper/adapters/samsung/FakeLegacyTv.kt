package io.github.throughthewind.zapper.adapters.samsung

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A 2008–2013 Samsung TV on localhost speaking [LegacyProtocol], written from the packet layout
 * rather than with the protocol object under test, so a framing mistake fails here.
 *
 * [answer] decides the reply to each handshake from the remote id the phone sent.
 */
internal class FakeLegacyTv(
    private val answer: (remoteId: String) -> List<ByteArray> = { listOf(GRANTED) },
) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    val host: String = "127.0.0.1"

    val remoteIds = CopyOnWriteArrayList<String>()
    val names = CopyOnWriteArrayList<String>()
    val keys = CopyOnWriteArrayList<String>()
    private val clients = CopyOnWriteArrayList<Socket>()

    init {
        Thread({
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                clients += client
                Thread({ serve(client) }, "fake-legacy-tv-client").apply { isDaemon = true }.start()
            }
        }, "fake-legacy-tv").apply { isDaemon = true }.start()
    }

    private fun serve(client: Socket) {
        runCatching {
            val input = client.getInputStream()
            val output = client.getOutputStream()
            while (true) {
                val payload = ByteArrayInputStream(readPacket(input))
                val head = payload.readNBytes(2)
                if (head.contentEquals(byteArrayOf(0x64, 0x00))) {
                    readString(payload) // the phone's address
                    val id = readString(payload)
                    remoteIds += id
                    names += readString(payload)
                    answer(id).forEach { write(output, it) }
                } else {
                    payload.readNBytes(1)
                    keys += readString(payload)
                    write(output, KEY_ACCEPTED)
                }
            }
        }
    }

    /** Drops every open connection, as a TV turned off would. */
    fun dropClients() = clients.forEach { runCatching { it.close() } }

    override fun close() {
        server.close()
        dropClients()
    }

    companion object {
        val GRANTED = byteArrayOf(0x64, 0x00, 0x01, 0x00)
        val DENIED = byteArrayOf(0x64, 0x00, 0x00, 0x00)
        val WAITING = byteArrayOf(0x0a, 0x00, 0x02, 0x00, 0x00, 0x00)
        val CANCELLED = byteArrayOf(0x65, 0x00)
        val KEY_ACCEPTED = byteArrayOf(0x00, 0x00, 0x00, 0x00)

        private fun readPacket(input: InputStream): ByteArray {
            if (input.read() < 0) throw EOFException()
            input.readNBytes(length(input))
            return input.readNBytes(length(input))
        }

        private fun length(input: InputStream): Int {
            val b = input.readNBytes(2)
            if (b.size < 2) throw EOFException()
            return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)
        }

        private fun readString(input: InputStream): String = String(Base64.getDecoder().decode(input.readNBytes(length(input))))

        private fun write(
            output: OutputStream,
            payload: ByteArray,
        ) {
            val app = "iapp.samsung".toByteArray()
            output.write(byteArrayOf(0x00, app.size.toByte(), 0x00) + app + byteArrayOf(payload.size.toByte(), 0x00) + payload)
            output.flush()
        }
    }
}
