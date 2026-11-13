package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RepairTest {
    private val device = TvDevice("tv-1", "Living room", "192.0.2.10", "scripted")
    private val credentials = InMemoryCredentialStore()

    /** A TV that accepts only [validToken], or pairs (issuing it) when the phone has no token. */
    private inner class ScriptedTv(
        private val validToken: String = "new",
        private val certificateChanged: Boolean = false,
    ) : TvAdapter {
        override val id = "scripted"
        override val displayName = "Scripted"
        var sessions = 0

        override fun createSession(
            device: TvDevice,
            credentials: CredentialStore,
        ): TvSession {
            sessions++
            return object : TvSession {
                override val device = device
                override val capabilities: StateFlow<Set<Capability>> = MutableStateFlow(setOf(Capability.KEYS))
                override val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

                override suspend fun connect() {
                    if (certificateChanged && credentials.read(device.id, "pin") != null) {
                        throw TvException(TvError.CertificateChanged)
                    }
                    when (credentials.token(device.id)) {
                        null -> credentials.saveToken(device.id, validToken)
                        validToken -> Unit
                        else -> throw TvException(TvError.TokenRejected)
                    }
                    state.value = ConnectionState.Connected
                }

                override fun supports(key: RemoteKey) = true

                override suspend fun send(
                    key: RemoteKey,
                    action: KeyAction,
                ) = Unit

                override suspend fun disconnect() = Unit
            }
        }
    }

    @Test
    fun `a valid token connects without pairing again`() =
        runTest {
            credentials.saveToken(device.id, "new")
            val tv = ScriptedTv()
            val reasons = mutableListOf<RepairReason>()

            connectOrRepair(device, tv, credentials, onSession = {}, onRepair = { reasons += it })

            assertEquals(1, tv.sessions)
            assertEquals(emptyList(), reasons)
        }

    @Test
    fun `a rejected token is forgotten and pairing runs again, keeping other secrets`() =
        runTest {
            credentials.saveToken(device.id, "old")
            credentials.write(device.id, "other", "secret")
            val tv = ScriptedTv()
            val reasons = mutableListOf<RepairReason>()

            val session = connectOrRepair(device, tv, credentials, onSession = {}, onRepair = { reasons += it })

            assertEquals(ConnectionState.Connected, session.state.value)
            assertEquals(listOf(RepairReason.TokenRejected), reasons)
            assertEquals("new", credentials.token(device.id))
            assertEquals("secret", credentials.read(device.id, "other"))
            assertEquals(2, tv.sessions)
        }

    @Test
    fun `a changed certificate is not re-trusted without the user`() =
        runTest {
            credentials.saveToken(device.id, "new")
            credentials.write(device.id, "pin", "aa")
            val tv = ScriptedTv(certificateChanged = true)

            val error = assertFailsWith<TvException> { connectOrRepair(device, tv, credentials, onSession = {}) }

            assertEquals(TvError.CertificateChanged, error.error)
            assertEquals("aa", credentials.read(device.id, "pin"))
            assertEquals(1, tv.sessions)
        }

    @Test
    fun `forgetAndConnect pairs from scratch`() =
        runTest {
            credentials.saveToken(device.id, "new")
            credentials.write(device.id, "pin", "aa")
            val tv = ScriptedTv(certificateChanged = true)

            forgetAndConnect(device, tv, credentials, onSession = {})

            assertNull(credentials.read(device.id, "pin"))
            assertEquals("new", credentials.token(device.id))
        }
}
