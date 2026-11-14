package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.PowerState
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Power on with Wake-on-LAN and the power state from the info endpoint (SPEC F7). */
class SamsungPowerTest {
    private val ru7020 = javaClass.getResource("/api-v2-2019-ru7020.json")!!.readText()
    private val server = MockWebServer()
    private val wakes = mutableListOf<Pair<String, String>>()

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.close()

    private val adapter
        get() =
            SamsungAdapter(
                httpClient = OkHttpClient(),
                appName = "Zapper",
                ssdpSearch = { emptyFlow() },
                infoPort = server.port,
                subnetHosts = { emptyFlow() },
                wake = { mac, host -> wakes += mac to host },
            )

    private fun tv(mac: String? = "aa:bb:cc:dd:ee:ff") =
        TvDevice("tv-1", "Living room", server.hostName, SamsungAdapter.ID, macAddress = mac)

    private fun answer(body: String) = server.enqueue(MockResponse.Builder().body(body).build())

    @Test
    fun `a TV with a known MAC is woken with Wake-on-LAN`() =
        runBlocking {
            assertTrue(adapter.canPowerOn(tv()))
            assertFalse(adapter.canPowerOn(tv(mac = null)))

            adapter.powerOn(tv())

            assertEquals(listOf("aa:bb:cc:dd:ee:ff" to server.hostName), wakes)
        }

    @Test
    fun `the power state comes from PowerState`() =
        runBlocking {
            answer(ru7020)
            assertEquals(PowerState.ON, adapter.powerState(tv()))

            answer(ru7020.replace("\"PowerState\":\"on\"", "\"PowerState\":\"standby\""))
            assertEquals(PowerState.STANDBY, adapter.powerState(tv()))
        }

    @Test
    fun `an older TV that answers without PowerState is on, one that does not answer is unknown`() =
        runBlocking {
            answer(ru7020.replace("\"PowerState\":\"on\",", ""))
            assertEquals(PowerState.ON, adapter.powerState(tv()))

            server.close()
            assertNull(adapter.powerState(tv()))
        }

    @Test
    fun `the help names the TV setting`() {
        assertTrue("Power On with Mobile" in adapter.powerOnHelp)
    }
}
