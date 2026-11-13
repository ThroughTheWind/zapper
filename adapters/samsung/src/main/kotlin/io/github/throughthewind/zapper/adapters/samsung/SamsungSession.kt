package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.CredentialStore
import io.github.throughthewind.zapper.core.remote.TvSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal class SamsungSession(
    override val device: TvDevice,
    private val credentials: CredentialStore,
    private val client: OkHttpClient,
    private val appName: String,
    private val scheme: String = "wss",
    private val port: Int = SamsungProtocol.SECURE_PORT,
    private val pairingTimeoutMs: Long = 60_000,
) : TvSession {
    override val capabilities: Set<Capability> = setOf(Capability.KEYS)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var socket: WebSocket? = null

    override suspend fun connect() {
        if (_state.value == ConnectionState.Connected) return
        _state.value = ConnectionState.Connecting
        val token = credentials.token(device.id)
        val connected = CompletableDeferred<String?>()
        val url = SamsungProtocol.channelUrl(scheme, device.host, port, appName, token)

        socket =
            client.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        // A TV that has never seen this token now shows its "Allow?" prompt.
                        if (token == null) _state.value = ConnectionState.AwaitingPairing(pinRequired = false)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        when (val event = SamsungProtocol.parse(text)) {
                            is SamsungProtocol.Event.Connected -> {
                                connected.complete(event.token)
                            }

                            SamsungProtocol.Event.Unauthorized -> {
                                connected.completeExceptionally(IllegalStateException("The TV refused the connection"))
                            }

                            SamsungProtocol.Event.Timeout -> {
                                connected.completeExceptionally(IllegalStateException("Nobody answered the prompt on the TV"))
                            }

                            is SamsungProtocol.Event.Other -> {
                                Unit
                            }
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        if (!connected.completeExceptionally(t)) {
                            _state.value = ConnectionState.Failed(t.message ?: "Connection lost", t)
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        _state.value = ConnectionState.Disconnected
                    }
                },
            )

        try {
            val newToken = withTimeout(pairingTimeoutMs) { connected.await() }
            if (newToken != null && newToken != token) credentials.saveToken(device.id, newToken)
            _state.value = ConnectionState.Connected
        } catch (e: Exception) {
            socket?.cancel()
            socket = null
            _state.value = ConnectionState.Failed(e.message ?: "Could not connect", e)
            throw e
        }
    }

    override suspend fun send(key: RemoteKey) {
        val code = key.samsungCode() ?: return
        val ws = checkNotNull(socket) { "Not connected" }
        check(ws.send(SamsungProtocol.keyPress(code))) { "The connection to the TV is closed" }
    }

    override suspend fun disconnect() {
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        _state.value = ConnectionState.Disconnected
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
    }
}
