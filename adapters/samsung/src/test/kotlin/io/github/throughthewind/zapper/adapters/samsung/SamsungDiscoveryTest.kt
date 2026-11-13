package io.github.throughthewind.zapper.adapters.samsung

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamsungDiscoveryTest {
    private val ru7020 = javaClass.getResource("/api-v2-2019-ru7020.json")!!.readText()
    private val server = MockWebServer()

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.close()

    private fun adapter(
        ssdpHosts: List<String> = emptyList(),
        subnetHosts: List<String> = emptyList(),
    ) = SamsungAdapter(
        httpClient = OkHttpClient(),
        appName = "Zapper",
        ssdpSearch = { flowOf(*ssdpHosts.map { Ssdp.Response(it, emptyMap()) }.toTypedArray()) },
        infoPort = server.port,
        subnetHosts = { subnetHosts.asFlow() },
    )

    @Test
    fun `the info endpoint describes the reference TV`() {
        val tv = SamsungDeviceInfo.parse("192.168.1.20", ru7020)!!

        assertEquals("Samsung 7 Series (75)", tv.name)
        assertEquals("UE75RU7020", tv.model)
        assertEquals("aa:bb:cc:dd:ee:ff", tv.macAddress)
        assertEquals("uuid:3e7a1c52-0000-4000-8000-00000000abcd", tv.uniqueId)
        assertEquals("true", tv.properties[SamsungDeviceInfo.PROPERTY_TOKEN_AUTH])
        assertEquals("on", tv.properties[SamsungDeviceInfo.PROPERTY_POWER_STATE])
    }

    @Test
    fun `an answer that is not a Samsung TV is ignored`() {
        assertNull(SamsungDeviceInfo.parse("192.168.1.30", """{"device":{"OS":"webOS","type":"LG"}}"""))
        assertNull(SamsungDeviceInfo.parse("192.168.1.30", "<html>router</html>"))
    }

    @Test
    fun `probing a Samsung TV's address finds it`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().body(ru7020).build())

            val tv = adapter().probe(server.hostName)

            assertEquals(SamsungAdapter.ID, tv?.adapterId)
            assertEquals(server.hostName, tv?.host)
        }

    @Test
    fun `probing an address that does not answer finds nothing`() =
        runBlocking {
            server.close()
            assertNull(adapter().probe("127.0.0.1"))
        }

    @Test
    fun `discovery probes each answering host once`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().body(ru7020).build())

            val found = adapter(listOf(server.hostName, server.hostName)).discover().toList()

            assertEquals(1, found.size)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a TV found only by the subnet sweep is listed once`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().body(ru7020).build())

            val found = adapter(ssdpHosts = listOf(server.hostName), subnetHosts = listOf(server.hostName)).discover().toList()
            assertEquals(1, found.size)

            server.enqueue(MockResponse.Builder().body(ru7020).build())
            val sweptOnly = adapter(subnetHosts = listOf(server.hostName)).discover().toList()
            assertEquals(server.hostName, sweptOnly.single().host)
        }

    @Test
    fun `the sweep covers the phone's subnet, nearest first, and skips large networks`() {
        val hosts = Ipv4Network(byteArrayOf(192.toByte(), 168.toByte(), 1, 20), 24).hosts()

        assertEquals(253, hosts.size)
        assertEquals(listOf("192.168.1.19", "192.168.1.21"), hosts.take(2).sorted())
        assertTrue("192.168.1.20" !in hosts && "192.168.1.0" !in hosts && "192.168.1.255" !in hosts)
        assertEquals(emptyList(), Ipv4Network(byteArrayOf(10, 0, 0, 5), 8).hosts())
    }

    @Test
    fun `the sweep finds a host that accepts the port`() =
        runBlocking {
            val local = Ipv4Network(byteArrayOf(127, 0, 0, 2), 30)
            val found = SubnetScan(server.port, localNetworks = { listOf(local) }).hosts().toList()

            assertEquals(listOf("127.0.0.1"), found)
        }

    @Test
    fun `discovery searches every target a Samsung TV answers`() {
        assertEquals("urn:samsung.com:device:RemoteControlReceiver:1", SamsungAdapter.SEARCH_TARGETS.first())
        assertTrue("urn:dial-multiscreen-org:service:dial:1" in SamsungAdapter.SEARCH_TARGETS)
    }

    @Test
    fun `an M-SEARCH asks for the remote control receiver`() {
        val request = Ssdp.mSearch(SamsungAdapter.SEARCH_TARGETS.first())

        assertTrue(request.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue("ST: urn:samsung.com:device:RemoteControlReceiver:1\r\n" in request)
        assertTrue(request.endsWith("\r\n\r\n"))
    }

    @Test
    fun `SSDP headers are read case-insensitively`() {
        val headers = Ssdp.parseHeaders("HTTP/1.1 200 OK\r\nLocation: http://192.168.1.20:7676/rcr/\r\nst: urn:x\r\n\r\n")

        assertEquals("http://192.168.1.20:7676/rcr/", headers["LOCATION"])
        assertEquals("urn:x", headers["ST"])
    }
}
