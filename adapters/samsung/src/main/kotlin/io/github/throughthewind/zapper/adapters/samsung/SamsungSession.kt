package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.CredentialStore
import io.github.throughthewind.zapper.core.remote.PairingRequest
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import io.github.throughthewind.zapper.core.remote.saveToken
import io.github.throughthewind.zapper.core.remote.token
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
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
    override val capabilities: StateFlow<Set<Capability>> =
        MutableStateFlow(setOf(Capability.KEYS, Capability.LONG_PRESS)).asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile private var socket: WebSocket? = null

    override suspend fun connect() {
        if (_state.value == ConnectionState.Connected) return
        _state.value = ConnectionState.Connecting
        val token = credentials.token(device.id)
        val trust = if (scheme == "wss") PinningTrustManager(credentials.read(device.id, CERT_PIN)) else null
        val wsClient = if (trust != null) TvTls.pinning(client, device.host, trust) else client
        val connected = CompletableDeferred<String?>()
        val url = SamsungProtocol.channelUrl(scheme, device.host, port, appName, token)

        socket =
            wsClient.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        // A TV that has never seen this phone now shows its "Allow?" prompt.
                        if (token == null) {
                            _state.value = ConnectionState.AwaitingPairing(PairingRequest.ConfirmOnTv(pairingTimeoutMs))
                        }
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
                                connected.completeExceptionally(
                                    TvException(if (token == null) TvError.PairingRefused else TvError.TokenRejected),
                                )
                            }

                            SamsungProtocol.Event.Timeout -> {
                                connected.completeExceptionally(TvException(TvError.PairingTimedOut))
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
                        val error = if (trust?.mismatch == true) TvError.CertificateChanged else TvError.Unreachable()
                        if (!connected.completeExceptionally(TvException(error, t))) {
                            socket = null
                            _state.value = ConnectionState.Failed(TvError.ConnectionLost())
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (socket === webSocket) {
                            socket = null
                            _state.value = ConnectionState.Disconnected
                        }
                    }
                },
            )

        try {
            val newToken = withTimeout(pairingTimeoutMs) { connected.await() }
            if (newToken != null && newToken != token) credentials.saveToken(device.id, newToken)
            val seen = trust?.seen
            if (seen != null && credentials.read(device.id, CERT_PIN) == null) credentials.write(device.id, CERT_PIN, seen)
            _state.value = ConnectionState.Connected
        } catch (e: Exception) {
            socket?.cancel()
            socket = null
            val error =
                when (e) {
                    is TvException -> e
                    is TimeoutCancellationException -> TvException(TvError.PairingTimedOut, e)
                    else -> TvException(TvError.Unreachable(), e)
                }
            _state.value = ConnectionState.Failed(error.error)
            throw error
        }
    }

    override fun supports(key: RemoteKey): Boolean = key.samsungCode() != null

    override suspend fun send(
        key: RemoteKey,
        action: KeyAction,
    ) {
        val ws = socket
        if (ws == null || _state.value != ConnectionState.Connected) throw TvException(TvError.NotConnected)
        val code = key.samsungCode() ?: return
        val cmd =
            when (action) {
                KeyAction.CLICK -> "Click"
                KeyAction.PRESS -> "Press"
                KeyAction.RELEASE -> "Release"
            }
        if (!ws.send(SamsungProtocol.keyPress(code, cmd))) throw TvException(TvError.ConnectionLost())
    }

    override suspend fun disconnect() {
        val ws = socket
        socket = null
        ws?.close(NORMAL_CLOSURE, null)
        _state.value = ConnectionState.Disconnected
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val CERT_PIN = "certPin"
    }
}
