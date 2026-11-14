package io.github.throughthewind.zapper.adapters.fake

import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.AdapterRegistry
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.InMemoryCredentialStore
import io.github.throughthewind.zapper.core.remote.PowerState
import io.github.throughthewind.zapper.core.remote.TvConnection
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.saveToken
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reconnection, backoff and the no-queue rule (SPEC F3), against the simulated TV. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvConnectionTest {
    private val tv = FakeTvAdapter(FakeTvBehaviour(discoveryDelayMs = 0, pairingDelayMs = 0))
    private val credentials = InMemoryCredentialStore()
    private val device = TvDevice("uuid:fake-tv-1", "Simulated TV", FakeTvAdapter.HOST, FakeTvAdapter.ID)
    private val saved = mutableListOf<TvDevice>()

    private fun TestScope.connection() =
        TvConnection(
            adapters = AdapterRegistry(listOf(tv)),
            credentials = credentials,
            scope = backgroundScope,
            onConnected = { saved += it },
            retryDelaysMs = listOf(1_000, 2_000, 4_000),
        )

    private suspend fun paired() = credentials.saveToken(device.id, "fake-token")

    @Test
    fun `a paired TV reconnects without pairing and is saved`() =
        runTest {
            paired()
            val connection = connection()

            connection.connect(device)
            runCurrent()

            assertEquals(ConnectionState.Connected, connection.state.value)
            assertEquals(listOf(device), saved)
        }

    @Test
    fun `an unreachable TV is retried with backoff`() =
        runTest {
            paired()
            tv.online = false
            val connection = connection()

            connection.connect(device)
            runCurrent()
            assertEquals(1, tv.connectAttempts)
            assertTrue(connection.state.value is ConnectionState.Failed)

            advanceTimeBy(1_001)
            assertEquals(2, tv.connectAttempts)
            advanceTimeBy(1_500)
            assertEquals(2, tv.connectAttempts, "the second wait is longer")
            advanceTimeBy(600)
            assertEquals(3, tv.connectAttempts)

            tv.online = true
            advanceTimeBy(4_001)
            assertEquals(ConnectionState.Connected, connection.state.value)
        }

    @Test
    fun `the network returning retries at once`() =
        runTest {
            paired()
            tv.online = false
            val connection = connection()
            connection.connect(device)
            runCurrent()

            tv.online = true
            connection.networkAvailable()
            runCurrent()

            assertEquals(ConnectionState.Connected, connection.state.value)
        }

    @Test
    fun `a dropped connection comes back`() =
        runTest {
            paired()
            val connection = connection()
            connection.connect(device)
            runCurrent()

            tv.dropConnections()
            runCurrent()
            assertEquals(ConnectionState.Failed(TvError.ConnectionLost()), connection.state.value)

            advanceTimeBy(1_001)
            assertEquals(ConnectionState.Connected, connection.state.value)
        }

    @Test
    fun `a refused pairing is not retried`() =
        runTest {
            val refusing = FakeTvAdapter(FakeTvBehaviour(pairingDelayMs = 0, refusePairing = true))
            val connection =
                TvConnection(AdapterRegistry(listOf(refusing)), credentials, backgroundScope, retryDelaysMs = listOf(1_000))
            connection.connect(device)
            advanceTimeBy(10_000)

            assertEquals(ConnectionState.Failed(TvError.PairingRefused), connection.state.value)
            assertEquals(1, refusing.connectAttempts)
        }

    @Test
    fun `a press while disconnected reconnects and is not replayed`() =
        runTest {
            paired()
            tv.online = false
            val connection = connection()
            connection.connect(device)
            runCurrent()

            tv.online = true
            connection.send(RemoteKey.VOLUME_UP)
            runCurrent()

            assertEquals(ConnectionState.Connected, connection.state.value)
            assertTrue(tv.received.isEmpty(), "the press that triggered the reconnect is dropped")
            connection.send(RemoteKey.VOLUME_UP)
            runCurrent()
            assertEquals(listOf("VOLUME_UP:CLICK"), tv.received)
        }

    @Test
    fun `pausing drops the connection and resuming restores it`() =
        runTest {
            paired()
            val connection = connection()
            connection.connect(device)
            runCurrent()

            connection.pause()
            runCurrent()
            assertEquals(ConnectionState.Disconnected, connection.state.value)
            tv.online = false
            advanceTimeBy(30_000)
            assertEquals(1, tv.connectAttempts, "no work in the background")

            tv.online = true
            connection.resume()
            runCurrent()
            assertEquals(ConnectionState.Connected, connection.state.value)
        }

    @Test
    fun `text reaches the TV and the TV keyboard is followed`() =
        runTest {
            paired()
            val connection = connection()
            connection.connect(device)
            runCurrent()
            assertTrue(Capability.TEXT_INPUT in connection.capabilities.value)

            tv.showKeyboard(true)
            runCurrent()
            assertTrue(connection.tvKeyboard.value)

            assertNull(connection.sendText("stranger"))
            runCurrent()
            assertEquals(listOf("TEXT:stranger"), tv.received)
            assertFalse(connection.tvKeyboard.value)
        }

    @Test
    fun `text while disconnected is refused, not queued`() =
        runTest {
            paired()
            tv.online = false
            val connection = connection()
            connection.connect(device)
            runCurrent()

            assertEquals(TvError.NotConnected, connection.sendText("stranger"))
            assertTrue(connection.capabilities.value.isEmpty())
            assertTrue(tv.received.isEmpty())
        }

    @Test
    fun `an app shortcut opens the app, and one the TV lacks says so`() =
        runTest {
            paired()
            val connection = connection()
            connection.connect(device)
            runCurrent()
            assertTrue(Capability.APP_LAUNCH in connection.capabilities.value)

            assertNull(connection.launchApp("netflix"))
            assertEquals(TvError.AppNotInstalled, connection.launchApp("plex"))
            assertEquals(listOf("APP:netflix"), tv.received)
        }

    @Test
    fun `power on wakes a TV that is off, then connects`() =
        runTest {
            paired()
            tv.online = false
            val connection = connection()
            val withMac = device.copy(macAddress = "02:00:00:00:00:01")
            connection.connect(withMac)
            runCurrent()
            assertTrue(connection.canPowerOn())

            assertTrue(connection.powerOn())
            runCurrent()

            assertEquals(1, tv.wakeUps)
            assertEquals(PowerState.ON, connection.power.value)
            assertEquals(ConnectionState.Connected, connection.state.value)
        }

    @Test
    fun `power on gives up when the TV does not wake, resending the wake-up meanwhile`() =
        runTest {
            paired()
            val asleep = FakeTvAdapter(FakeTvBehaviour(pairingDelayMs = 0, wakesOnLan = false)).apply { online = false }
            val connection = TvConnection(AdapterRegistry(listOf(asleep)), credentials, backgroundScope, retryDelaysMs = listOf(60_000))
            connection.connect(device.copy(macAddress = "02:00:00:00:00:01"))
            runCurrent()

            assertFalse(connection.powerOn(timeoutMs = 10_000))
            assertTrue(asleep.wakeUps >= 3, "sent ${asleep.wakeUps} wake-ups")
        }

    @Test
    fun `a TV without a MAC address cannot be powered on`() =
        runTest {
            val connection = connection()
            connection.connect(device)
            runCurrent()

            assertFalse(connection.canPowerOn())
            assertFalse(connection.powerOn())
            assertEquals(0, tv.wakeUps)
        }
}
