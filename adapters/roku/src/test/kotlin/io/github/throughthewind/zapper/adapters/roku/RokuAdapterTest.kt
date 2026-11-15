package io.github.throughthewind.zapper.adapters.roku

import io.github.throughthewind.zapper.core.remote.PowerState
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RokuAdapterTest {
    private val tv = FakeRoku().start()
    private val woken = CopyOnWriteArrayList<String>()
    private val searched = CopyOnWriteArrayList<String>()

    @AfterEach
    fun stop() = tv.close()

    private fun adapter(
        ssdp: Flow<Ssdp.Response> = flowOf(),
        port: Int = tv.port,
    ) = RokuAdapter(
        httpClient = OkHttpClient(),
        ssdpSearch = { targets ->
            searched += targets
            ssdp
        },
        ecpPort = port,
        wake = { mac, _ -> woken += mac },
    )

    @Test
    fun `device-info describes a Roku TV`() {
        val roku = RokuDeviceInfo.parse("192.168.1.40", FakeRoku.TCL_TV)!!

        assertEquals(RokuAdapter.ID, roku.adapterId)
        assertEquals("Living room & kitchen", roku.name)
        assertEquals("TCL 55S435", roku.model)
        // On Ethernet, the wired MAC is the one Wake-on-LAN needs.
        assertEquals("d4:e2:2f:bc:5c:8b", roku.macAddress)
        assertEquals("uuid:29780015-2c06-1066-80c9-d4e22fbc5c8a", roku.uniqueId)
        assertEquals("true", roku.properties[RokuDeviceInfo.PROPERTY_IS_TV])
    }

    @Test
    fun `device-info describes a Roku player`() {
        val roku = RokuDeviceInfo.parse("192.168.1.41", FakeRoku.STREAMING_STICK)!!

        assertEquals("Roku Express - YH00AB000000", roku.name)
        assertEquals("Roku Express", roku.model)
        assertEquals("b0:a7:37:96:4d:fb", roku.macAddress)
        assertEquals("false", roku.properties[RokuDeviceInfo.PROPERTY_IS_TV])
    }

    @Test
    fun `an answer that is not a Roku is ignored`() {
        assertNull(RokuDeviceInfo.parse("192.168.1.30", "<html>router</html>"))
        assertNull(RokuDeviceInfo.parse("192.168.1.30", "<device-info><vendor-name>Other</vendor-name></device-info>"))
    }

    @Test
    fun `probing finds a Roku, and nothing where none answers`() =
        runBlocking {
            assertEquals("Living room & kitchen", adapter().probe("127.0.0.1")?.name)
            val closedPort = ServerSocket(0).use { it.localPort }
            assertNull(adapter(port = closedPort).probe("127.0.0.1"))
        }

    @Test
    fun `discovery searches roku-ecp and confirms each answer once`() =
        runBlocking {
            val answers = List(3) { Ssdp.Response("127.0.0.1", mapOf("ST" to "roku:ecp")) }
            val found = adapter(ssdp = flowOf(*answers.toTypedArray())).discover().toList()

            assertEquals(listOf("roku:ecp"), searched)
            assertEquals(1, found.size)
            assertEquals("127.0.0.1", found.single().host)
        }

    @Test
    fun `power state follows power-mode on a TV`() =
        runBlocking {
            val device = adapter().probe("127.0.0.1")!!.toDevice()
            assertEquals(PowerState.ON, adapter().powerState(device))
            tv.powerMode = "DisplayOff"
            assertEquals(PowerState.STANDBY, adapter().powerState(device))
            tv.powerMode = "Headless"
            assertEquals(PowerState.STANDBY, adapter().powerState(device))
        }

    @Test
    fun `a player has no power state and cannot be turned on`() =
        runBlocking {
            tv.deviceInfo = FakeRoku.STREAMING_STICK
            val device = adapter().probe("127.0.0.1")!!.toDevice()

            assertNull(adapter().powerState(device))
            assertFalse(adapter().canPowerOn(device))
            assertIs<TvError.Unsupported>(assertFailsWith<TvException> { adapter().powerOn(device) }.error)
        }

    @Test
    fun `a TV in Fast TV start standby wakes on PowerOn`() =
        runBlocking {
            tv.powerMode = "DisplayOff"
            val device = adapter().probe("127.0.0.1")!!.toDevice()
            assertTrue(adapter().canPowerOn(device))
            adapter().powerOn(device)

            assertEquals(listOf("keypress/PowerOn"), tv.commands)
            assertEquals(emptyList(), woken)
        }

    @Test
    fun `a TV in deep standby gets Wake-on-LAN`() =
        runBlocking {
            val device = adapter().probe("127.0.0.1")!!.toDevice()
            tv.close()
            adapter().powerOn(device)

            assertEquals(listOf("d4:e2:2f:bc:5c:8b"), woken)
        }

    @Test
    fun `a TV that blocks phones is not sent Wake-on-LAN`() =
        runBlocking {
            val device = adapter().probe("127.0.0.1")!!.toDevice()
            tv.limited = true
            val error = assertFailsWith<TvException> { adapter().powerOn(device) }

            assertIs<TvError.ControlDisabled>(error.error)
            assertEquals(emptyList(), woken)
        }
}
