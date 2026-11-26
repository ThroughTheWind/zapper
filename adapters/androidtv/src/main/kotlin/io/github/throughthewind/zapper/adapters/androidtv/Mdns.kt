package io.github.throughthewind.zapper.adapters.androidtv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Minimal mDNS browse (RFC 6762) for one service type. The query goes out from an ordinary port, so
 * responders answer it by unicast to that port (§6.7) and no socket has to join the group. Each
 * device that answers is emitted with its instance name, such as "Living Room TV". On Android the
 * caller holds a Wi-Fi multicast lock.
 */
internal class Mdns(
    private val group: InetSocketAddress = InetSocketAddress(InetAddress.getByName("224.0.0.251"), 5353),
    private val resendEveryMs: Long = 3_000,
) {
    data class Instance(
        val host: String,
        val name: String?,
    )

    fun browse(service: String): Flow<Instance> =
        flow {
            DatagramSocket().use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val query = query(service)
                var lastSent = 0L
                val buffer = ByteArray(9000)
                while (currentCoroutineContext().isActive) {
                    val now = System.currentTimeMillis()
                    if (now - lastSent >= resendEveryMs) {
                        socket.send(DatagramPacket(query, query.size, group))
                        lastSent = now
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val names = runCatching { instanceNames(packet.data.copyOf(packet.length), service) }.getOrDefault(emptyList())
                    if (names.isNotEmpty()) emit(Instance(packet.address.hostAddress, names.first()))
                }
            }
        }.flowOn(Dispatchers.IO)

    companion object {
        private const val SOCKET_TIMEOUT_MS = 500
        private const val TYPE_PTR = 12
        private const val CLASS_IN_UNICAST_RESPONSE = 0x8001

        /** A one-question PTR query for [service], such as `_androidtvremote2._tcp.local`. */
        fun query(service: String): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(ByteArray(4)) // id 0, flags 0
            out.write(byteArrayOf(0, 1, 0, 0, 0, 0, 0, 0)) // one question
            writeName(out, service)
            out.write(byteArrayOf(0, TYPE_PTR.toByte()))
            out.write(byteArrayOf((CLASS_IN_UNICAST_RESPONSE shr 8).toByte(), CLASS_IN_UNICAST_RESPONSE.toByte()))
            return out.toByteArray()
        }

        /** The instance labels of every PTR record for [service] in a response, in any section. */
        fun instanceNames(
            packet: ByteArray,
            service: String,
        ): List<String> {
            if (packet.size < 12) return emptyList()

            fun u16(at: Int): Int {
                if (at + 2 > packet.size) throw IOException("Truncated record")
                return ((packet[at].toInt() and 0xFF) shl 8) or (packet[at + 1].toInt() and 0xFF)
            }
            val questions = u16(4)
            val records = u16(6) + u16(8) + u16(10)
            var pos = 12
            repeat(questions) { pos = readName(packet, pos).second + 4 }
            val names = mutableListOf<String>()
            repeat(records) {
                val (owner, afterName) = readName(packet, pos)
                val type = u16(afterName)
                val length = u16(afterName + 8)
                val data = afterName + 10
                if (type == TYPE_PTR && owner.joinToString(".").equals(service, ignoreCase = true)) {
                    readName(packet, data).first.firstOrNull()?.let { names += it }
                }
                pos = data + length
            }
            return names
        }

        private fun writeName(
            out: ByteArrayOutputStream,
            name: String,
        ) {
            for (label in name.split('.').filter { it.isNotEmpty() }) {
                val bytes = label.toByteArray(Charsets.UTF_8)
                out.write(bytes.size)
                out.write(bytes)
            }
            out.write(0)
        }

        /** The labels of the name at [start], following compression pointers, and the offset after it. */
        private fun readName(
            packet: ByteArray,
            start: Int,
        ): Pair<List<String>, Int> {
            val labels = mutableListOf<String>()
            var pos = start
            var end = -1
            var jumps = 0
            while (true) {
                if (pos >= packet.size) throw IOException("Truncated name")
                val length = packet[pos].toInt() and 0xFF
                when {
                    length == 0 -> {
                        if (end < 0) end = pos + 1
                        return labels to end
                    }

                    length and 0xC0 == 0xC0 -> {
                        if (pos + 1 >= packet.size || ++jumps > 16) throw IOException("Bad name pointer")
                        if (end < 0) end = pos + 2
                        pos = ((length and 0x3F) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                    }

                    else -> {
                        if (pos + 1 + length > packet.size) throw IOException("Truncated label")
                        labels += String(packet, pos + 1, length, Charsets.UTF_8)
                        pos += 1 + length
                    }
                }
            }
        }
    }
}
