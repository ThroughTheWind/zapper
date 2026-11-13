package io.github.throughthewind.zapper.adapters.samsung

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Finds hosts on the phone's own subnets that accept a TCP connection on [port]. The fallback for
 * networks that drop SSDP multicast (mesh routers, some ISP boxes): research §1 recommends probing
 * the TV's known port when multicast finds nothing.
 */
internal class SubnetScan(
    private val port: Int,
    private val connectTimeoutMs: Int = 400,
    private val parallelism: Int = 48,
    private val localNetworks: () -> List<Ipv4Network> = ::siteLocalNetworks,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun hosts(): Flow<String> =
        localNetworks()
            .flatMap { it.hosts() }
            .distinct()
            .asFlow()
            .flatMapMerge(parallelism) { host -> flow { if (accepts(host)) emit(host) } }
            .flowOn(Dispatchers.IO)

    private fun accepts(host: String): Boolean =
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), connectTimeoutMs) } }.isSuccess

    companion object {
        /** Larger subnets are not swept: 1022 hosts is already several seconds of probing. */
        const val MIN_PREFIX = 22

        /** The phone's private IPv4 networks that are up, such as Wi-Fi or Ethernet. */
        fun siteLocalNetworks(): List<Ipv4Network> =
            runCatching {
                NetworkInterface
                    .getNetworkInterfaces()
                    .toList()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { it.interfaceAddresses }
                    .mapNotNull { address ->
                        val ip = address.address as? Inet4Address ?: return@mapNotNull null
                        if (!ip.isSiteLocalAddress) null else Ipv4Network(ip.address, address.networkPrefixLength.toInt())
                    }
            }.getOrDefault(emptyList())
    }
}

/** An IPv4 address with its prefix length, for example 192.168.1.20/24. */
internal class Ipv4Network(
    address: ByteArray,
    private val prefix: Int,
) {
    private val ip = address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }

    /** Every host address of the network except this one, nearest first; empty if too large. */
    fun hosts(): List<String> {
        if (prefix < SubnetScan.MIN_PREFIX || prefix > 30) return emptyList()
        val size = 1L shl (32 - prefix)
        val base = ip and (size - 1).inv() and 0xffffffffL
        return (1 until size - 1)
            .map { base + it }
            .filter { it != ip }
            .sortedBy { kotlin.math.abs(it - ip) }
            .map { addr -> (3 downTo 0).joinToString(".") { shift -> ((addr shr (shift * 8)) and 0xff).toString() } }
    }
}
