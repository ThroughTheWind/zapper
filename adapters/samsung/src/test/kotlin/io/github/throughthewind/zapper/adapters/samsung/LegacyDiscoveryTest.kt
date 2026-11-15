package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.InMemoryCredentialStore
import io.github.throughthewind.zapper.core.remote.PowerState
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Finding a 2008–2013 Samsung TV, which has no info endpoint, and routing it to the legacy session. */
class LegacyDiscoveryTest {
    private val tv = FakeLegacyTv()

    /** A port nothing listens on, standing in for the missing Tizen info endpoint. */
    private val closedPort = ServerSocket(0).use { it.localPort }

    @AfterEach
    fun stop() = tv.close()

    private fun adapter(
        ssdp: List<Ssdp.Response> = emptyList(),
        legacyPort: Int = tv.port,
    ) = SamsungAdapter(
        httpClient = OkHttpClient(),
        appName = "Zapper",
        ssdpSearch = { flowOf(*ssdp.toTypedArray()) },
        infoPort = closedPort,
        subnetHosts = { emptyFlow() },
        legacyPort = legacyPort,
    )

    private val samsungAnswer =
        Ssdp.Response(
            "127.0.0.1",
            mapOf(
                "SERVER" to "SHP, UPnP/1.0, Samsung UPnP SDK/1.0",
                "USN" to "uuid:0ee6b280-00fa-1000-a8fb-c0f2aa8d1234::urn:samsung.com:device:RemoteControlReceiver:1",
            ),
        )

    @Test
    fun `an address with only the legacy port open is a legacy Samsung TV`() =
        runBlocking {
            val found = adapter().probe("127.0.0.1")!!

            assertEquals(SamsungAdapter.ID, found.adapterId)
            assertEquals(SamsungAdapter.PROTOCOL_LEGACY, found.properties[SamsungAdapter.PROPERTY_PROTOCOL])
            assertNull(found.macAddress)
            assertFalse(adapter().canPowerOn(found.toDevice()))
        }

    @Test
    fun `an address with neither port open is not a Samsung TV`() =
        runBlocking {
            assertNull(adapter(legacyPort = closedPort).probe("127.0.0.1"))
        }

    @Test
    fun `discovery lists a legacy TV that answered SSDP as Samsung, under its UPnP id`() =
        runBlocking {
            val found = adapter(listOf(samsungAnswer)).discover().toList()

            assertEquals("uuid:0ee6b280-00fa-1000-a8fb-c0f2aa8d1234", found.single().uniqueId)
        }

    @Test
    fun `discovery ignores an SSDP answer from another brand, even with the port open`() =
        runBlocking {
            val other = Ssdp.Response("127.0.0.1", mapOf("SERVER" to "Linux/4.4 UPnP/1.0 LG WebOS"))

            assertEquals(emptyList(), adapter(listOf(other)).discover().toList())
        }

    @Test
    fun `a legacy TV gets the legacy session and is on while it answers`() =
        runBlocking {
            val device = adapter().probe("127.0.0.1")!!.toDevice()

            assertIs<LegacySamsungSession>(adapter().createSession(device, InMemoryCredentialStore()))
            assertEquals(PowerState.ON, adapter().powerState(device))
            assertNull(adapter(legacyPort = closedPort).powerState(device))
        }

    @Test
    fun `a TV saved before legacy support keeps the Tizen session`() {
        val tizen = TvDevice("tv-2", "Living room", "127.0.0.1", SamsungAdapter.ID, properties = mapOf("tokenAuth" to "true"))

        assertIs<SamsungSession>(adapter().createSession(tizen, InMemoryCredentialStore()))
    }
}
