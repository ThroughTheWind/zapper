package io.github.throughthewind.zapper.adapters.roku

import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.InMemoryCredentialStore
import io.github.throughthewind.zapper.core.remote.TvApp
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.TvSessionContract
import io.github.throughthewind.zapper.core.remote.token
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drives [RokuSession] against [FakeRoku], a Roku answering ECP on localhost. */
class RokuSessionTest : TvSessionContract() {
    private val tv = FakeRoku().start()
    private val credentials = InMemoryCredentialStore()

    @AfterEach
    fun stop() = tv.close()

    private val device =
        TvDevice(
            id = "uuid:roku",
            name = "Roku TV",
            host = "127.0.0.1",
            adapterId = RokuAdapter.ID,
            properties = mapOf(RokuDeviceInfo.PROPERTY_IS_TV to "true"),
        )

    private fun session(port: Int = tv.port): TvSession =
        RokuAdapter(OkHttpClient(), { emptyFlow() }, port).createSession(device, credentials)

    override fun newSession(): TvSession = session()

    override fun keysReceived(): List<String> = tv.commands

    private fun connected(): TvSession = session().also { runBlocking { it.connect() } }

    @Test
    fun `connecting needs no pairing and stores nothing`() =
        runBlocking {
            val session = session()
            session.connect()

            assertEquals(ConnectionState.Connected, session.state.value)
            assertNull(credentials.token("uuid:roku"))
        }

    @Test
    fun `keys are ECP keypresses`() =
        runBlocking {
            val session = connected()
            session.send(RemoteKey.HOME)
            session.send(RemoteKey.OK)
            session.send(RemoteKey.PLAY_PAUSE)
            session.send(RemoteKey.HDMI_2)

            assertEquals(listOf("keypress/Home", "keypress/Select", "keypress/Play", "keypress/InputHDMI2"), tv.commands)
        }

    @Test
    fun `holding a key sends keydown then keyup`() =
        runBlocking {
            val session = connected()
            assertTrue(Capability.LONG_PRESS in session.capabilities.value)
            session.send(RemoteKey.RIGHT, KeyAction.PRESS)
            session.send(RemoteKey.RIGHT, KeyAction.RELEASE)

            assertEquals(listOf("keydown/Right", "keyup/Right"), tv.commands)
        }

    @Test
    fun `undocumented keys are unsupported and never sent`() =
        runBlocking {
            val session = connected()
            for (key in listOf(RemoteKey.DIGIT_1, RemoteKey.SOURCE, RemoteKey.RED, RemoteKey.SETTINGS, RemoteKey.EXIT)) {
                assertFalse(session.supports(key), "$key")
                session.send(key)
            }
            assertEquals(emptyList(), tv.commands)
        }

    @Test
    fun `power turns an on TV off and a TV in standby on`() =
        runBlocking {
            val session = connected()
            assertTrue(session.supports(RemoteKey.POWER))
            session.send(RemoteKey.POWER)
            tv.powerMode = "DisplayOff"
            session.send(RemoteKey.POWER)

            assertEquals(listOf("keypress/PowerOff", "keypress/PowerOn"), tv.commands)
        }

    @Test
    fun `a stick has no power, inputs, channels or volume`() =
        runBlocking {
            tv.deviceInfo = FakeRoku.STREAMING_STICK
            // Saved as a TV, so this also shows the session trusting what the Roku says at connect.
            val session = connected()
            for (key in listOf(RemoteKey.POWER, RemoteKey.HDMI_1, RemoteKey.CHANNEL_UP, RemoteKey.VOLUME_UP, RemoteKey.MUTE)) {
                assertFalse(session.supports(key), "$key")
            }
            assertTrue(session.supports(RemoteKey.HOME))
            session.send(RemoteKey.POWER)
            assertEquals(emptyList(), tv.commands)
        }

    @Test
    fun `text is one Lit_ keypress per character, encoded, then Enter`() =
        runBlocking {
            connected().sendText("a b+/é😀")

            assertEquals(
                listOf(
                    "keypress/Lit_a",
                    "keypress/Lit_%20",
                    "keypress/Lit_b",
                    "keypress/Lit_%2B",
                    "keypress/Lit_%2F",
                    "keypress/Lit_%C3%A9",
                    "keypress/Lit_%F0%9F%98%80",
                    "keypress/Enter",
                ),
                tv.commands,
            )
        }

    @Test
    fun `installed apps are the TV's channels without its inputs`() =
        runBlocking {
            assertEquals(
                listOf(
                    TvApp("12", "Netflix"),
                    TvApp("837", "YouTube"),
                    TvApp("2213", "Roku Media Player"),
                    TvApp("151908", "The Roku Channel & more"),
                ),
                connected().installedApps(),
            )
        }

    @Test
    fun `a known app opens under its Roku channel id`() =
        runBlocking {
            val session = connected()
            session.launchApp("netflix")
            session.launchApp("2213")

            assertEquals(listOf("launch/12", "launch/2213"), tv.commands)
        }

    @Test
    fun `an app the TV does not list is not installed`() =
        runBlocking {
            val error = assertFailsWith<TvException> { connected().launchApp("disney-plus") }
            assertEquals(TvError.AppNotInstalled, error.error)
            assertEquals(emptyList(), tv.commands)
        }

    @Test
    fun `a launch the TV answers 404 is not installed`() =
        runBlocking {
            val session = connected()
            // The list was read at connect; the Roku is the one that says no here.
            tv.apps = FakeRoku.APPS.replace("""<app id="837" type="appl" version="2.21.100100031">YouTube</app>""", "")
            val error = assertFailsWith<TvException> { session.launchApp("youtube") }
            assertEquals(TvError.AppNotInstalled, error.error)
            assertEquals(ConnectionState.Connected, session.state.value)
        }

    @Test
    fun `a Roku that blocks phones refuses the connection with how to fix it`() =
        runBlocking {
            tv.limited = true
            val session = session()
            val error = assertFailsWith<TvException> { session.connect() }

            val blocked = assertIs<TvError.ControlDisabled>(error.error)
            assertTrue("Control by mobile apps" in blocked.message)
            assertEquals(ConnectionState.Failed(blocked), session.state.value)
        }

    @Test
    fun `a Roku that starts blocking phones ends the session`() =
        runBlocking {
            val session = connected()
            tv.limited = true
            val error = assertFailsWith<TvException> { session.send(RemoteKey.HOME) }

            assertIs<TvError.ControlDisabled>(error.error)
            assertEquals(ConnectionState.Failed(error.error), session.state.value)
        }

    @Test
    fun `nothing listening is unreachable`() =
        runBlocking {
            val closedPort = ServerSocket(0).use { it.localPort }
            val session = session(closedPort)
            val error = assertFailsWith<TvException> { session.connect() }

            assertIs<TvError.Unreachable>(error.error)
        }

    @Test
    fun `a Roku that goes away loses the connection`() =
        runBlocking {
            val session = connected()
            tv.close()
            val error = assertFailsWith<TvException> { session.send(RemoteKey.HOME) }

            assertIs<TvError.ConnectionLost>(error.error)
            assertEquals(ConnectionState.Failed(error.error), session.state.value)
        }

    @Test
    fun `something else on port 8060 is not a Roku`() =
        runBlocking {
            tv.deviceInfo = "<html>router</html>"
            val error = assertFailsWith<TvException> { session().connect() }
            assertIs<TvError.Unreachable>(error.error)
        }
}
