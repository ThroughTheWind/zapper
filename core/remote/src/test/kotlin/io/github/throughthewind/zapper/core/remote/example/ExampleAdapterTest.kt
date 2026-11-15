package io.github.throughthewind.zapper.core.remote.example

import io.github.throughthewind.zapper.core.remote.InMemoryCredentialStore
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.TvSessionContract
import io.github.throughthewind.zapper.core.remote.saveToken
import io.github.throughthewind.zapper.core.remote.token
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The example adapter from docs/adapters.md against the shared contract, plus the pairing cases
 * every adapter adds (.ai/rules/adapters.md). A real adapter talks to a fake TV over its real
 * protocol, as SamsungSessionTest does with MockWebServer.
 */
class ExampleAdapterTest : TvSessionContract() {
    private val tv = FakeExampleTv()
    private val adapter = ExampleAdapter(tv)
    private val credentials = InMemoryCredentialStore()

    override fun newSession(): TvSession =
        runBlocking { adapter.createSession(adapter.probe(FakeExampleTv.HOST)!!.toDevice(), credentials) }

    override fun keysReceived(): List<String> = tv.keys

    @Test
    fun `probing another address finds nothing`() =
        runBlocking {
            assertNull(adapter.probe("192.168.1.20"))
        }

    @Test
    fun `pairing happens once, then the token is reused`() =
        runBlocking {
            newSession().connect()
            assertEquals(FakeExampleTv.TOKEN, credentials.token(FakeExampleTv.UUID))
            newSession().connect()
            assertEquals(1, tv.prompts)
        }

    @Test
    fun `a refused pairing fails with PairingRefused`() =
        runBlocking {
            tv.refuse = true
            val error = assertFailsWith<TvException> { newSession().connect() }
            assertEquals(TvError.PairingRefused, error.error)
        }

    @Test
    fun `a rejected token fails with TokenRejected`() =
        runBlocking {
            credentials.saveToken(FakeExampleTv.UUID, "stale")
            val error = assertFailsWith<TvException> { newSession().connect() }
            assertEquals(TvError.TokenRejected, error.error)
        }
}

/** An in-memory TV that answers [HOST], pairs once with a prompt and records the keys it gets. */
private class FakeExampleTv : ExampleTvApi {
    val keys = CopyOnWriteArrayList<String>()
    var prompts = 0
    var refuse = false

    override suspend fun info(host: String): ExampleTvInfo? =
        if (host == HOST) ExampleTvInfo(name = "Living room", model = "EX-1", macAddress = null, uuid = UUID) else null

    override suspend fun pair(
        host: String,
        token: String?,
    ): String {
        if (token != null) return if (token == TOKEN) token else throw TvException(TvError.TokenRejected)
        prompts++
        if (refuse) throw TvException(TvError.PairingRefused)
        return TOKEN
    }

    override suspend fun sendKey(
        host: String,
        token: String,
        code: String,
    ) {
        keys += code
    }

    companion object {
        const val HOST = "192.0.2.10"
        const val UUID = "uuid:example-1"
        const val TOKEN = "example-token"
    }
}
