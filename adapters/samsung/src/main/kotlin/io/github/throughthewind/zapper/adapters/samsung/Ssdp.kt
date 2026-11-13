package io.github.throughthewind.zapper.adapters.samsung

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Minimal SSDP search (UPnP discovery). Sends `M-SEARCH` to the multicast group and emits the
 * address of each device that answers. On Android the caller must hold a Wi-Fi multicast lock.
 */
internal class Ssdp(
    private val group: InetSocketAddress = InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900),
    private val resendEveryMs: Long = 3_000,
) {
    data class Response(
        val host: String,
        val headers: Map<String, String>,
    )

    /** Searches for every target in [searchTargets] until the collector stops, emitting each response as it arrives. */
    fun search(searchTargets: List<String>): Flow<Response> =
        flow {
            DatagramSocket().use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val requests = searchTargets.map { mSearch(it).toByteArray(Charsets.US_ASCII) }
                var lastSent = 0L
                val buffer = ByteArray(4096)
                while (currentCoroutineContext().isActive) {
                    val now = System.currentTimeMillis()
                    if (now - lastSent >= resendEveryMs) {
                        requests.forEach { socket.send(DatagramPacket(it, it.size, group)) }
                        lastSent = now
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    emit(Response(packet.address.hostAddress, parseHeaders(text)))
                }
            }
        }.flowOn(Dispatchers.IO)

    companion object {
        private const val SOCKET_TIMEOUT_MS = 500

        fun mSearch(searchTarget: String): String =
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: $searchTarget\r\n" +
                "\r\n"

        /** Header names are upper-cased, since devices disagree on their case. */
        fun parseHeaders(response: String): Map<String, String> =
            response
                .lineSequence()
                .drop(1)
                .mapNotNull { line ->
                    val colon = line.indexOf(':')
                    if (colon <= 0) null else line.substring(0, colon).trim().uppercase() to line.substring(colon + 1).trim()
                }.toMap()
    }
}
