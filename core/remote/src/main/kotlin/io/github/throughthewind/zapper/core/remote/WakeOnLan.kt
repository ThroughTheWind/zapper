package io.github.throughthewind.zapper.core.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Wake-on-LAN: a UDP "magic packet" of six 0xFF bytes and sixteen copies of the MAC, broadcast on
 * the local network (research §1). Never leaves the LAN (ADR-0003).
 */
object WakeOnLan {
    /** The ports TVs listen on for the packet; both are sent to, as both are common. */
    val PORTS = listOf(9, 7)

    private const val MAC_BYTES = 6
    private const val REPEATS = 16

    /** The magic packet for [mac], written `aa:bb:cc:dd:ee:ff` or with dashes. */
    fun packet(mac: String): ByteArray {
        val bytes = mac.split(':', '-').map { it.toInt(16).toByte() }
        require(bytes.size == MAC_BYTES) { "not a MAC address: $mac" }
        return ByteArray(MAC_BYTES) { 0xFF.toByte() } + List(REPEATS) { bytes }.flatten()
    }

    /**
     * Where to send: the limited broadcast, and the /24 broadcast of [host], since some routers
     * drop one or the other. A home network is almost always a /24.
     */
    fun broadcastAddresses(host: String): List<String> {
        val octets = host.split('.')
        val subnet = if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) "${octets.take(3).joinToString(".")}.255" else null
        return listOfNotNull("255.255.255.255", subnet)
    }

    /** Broadcasts the packet for [mac] towards [host]'s network. Throws [TvException] when nothing could be sent. */
    suspend fun send(
        mac: String,
        host: String,
    ) = withContext(Dispatchers.IO) {
        val packet =
            runCatching {
                packet(
                    mac,
                )
            }.getOrElse { throw TvException(TvError.Unsupported("turning on a TV without its MAC address"), it) }
        var sent = 0
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (address in broadcastAddresses(host)) {
                for (port in PORTS) {
                    runCatching {
                        socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(address), port))
                        sent++
                    }
                }
            }
        }
        if (sent == 0) throw TvException(TvError.Unreachable("The wake-up could not be sent"))
    }
}
