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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * A Samsung TV from 2008 to 2013 over [LegacyProtocol]. The TV has no token: it remembers the id
 * the phone sends, which is kept as this TV's token, so forgetting the token pairs again under a
 * new id (research §2.9).
 */
internal class LegacySamsungSession(
    override val device: TvDevice,
    private val credentials: CredentialStore,
    private val appName: String,
    private val port: Int = LegacyProtocol.PORT,
    private val connectTimeoutMs: Int = 3_000,
    private val pairingTimeoutMs: Long = 60_000,
) : TvSession {
    override val capabilities: StateFlow<Set<Capability>> = MutableStateFlow(setOf(Capability.KEYS)).asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile private var socket: Socket? = null

    override suspend fun connect() {
        if (_state.value == ConnectionState.Connected) return
        _state.value = ConnectionState.Connecting
        val savedId = credentials.token(device.id)
        val remoteId = savedId ?: UUID.randomUUID().toString()
        val answer = CompletableDeferred<LegacyProtocol.Reply>()
        val newSocket = Socket()
        socket = newSocket
        try {
            withContext(Dispatchers.IO) {
                try {
                    newSocket.connect(InetSocketAddress(device.host, port), connectTimeoutMs)
                } catch (e: IOException) {
                    throw TvException(TvError.Unreachable(), e)
                }
                val phoneIp = newSocket.localAddress.hostAddress
                newSocket.getOutputStream().write(LegacyProtocol.authenticate(phoneIp, remoteId, appName))
            }
            startReader(newSocket, answer)
            val reply = withTimeout(pairingTimeoutMs) { answer.await() }
            when (reply) {
                LegacyProtocol.Reply.Granted -> Unit
                LegacyProtocol.Reply.Denied -> throw TvException(if (savedId == null) TvError.PairingRefused else TvError.TokenRejected)
                LegacyProtocol.Reply.Cancelled -> throw TvException(TvError.PairingTimedOut)
                else -> throw TvException(UNSUPPORTED)
            }
            if (savedId == null) credentials.saveToken(device.id, remoteId)
            _state.value = ConnectionState.Connected
        } catch (e: Exception) {
            if (socket === newSocket) socket = null
            runCatching { newSocket.close() }
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

    /**
     * Reads the TV's packets on a thread of their own until the socket closes. The first answer to
     * the handshake settles [answer]; after that only a dropped connection matters.
     */
    private fun startReader(
        source: Socket,
        answer: CompletableDeferred<LegacyProtocol.Reply>,
    ) {
        val reader =
            Thread({
                try {
                    val input = source.getInputStream()
                    while (true) {
                        when (val reply = LegacyProtocol.reply(LegacyProtocol.readPayload(input))) {
                            // A TV that has never seen this id now shows its "Allow?" prompt.
                            LegacyProtocol.Reply.Waiting -> {
                                if (!answer.isCompleted) {
                                    _state.value = ConnectionState.AwaitingPairing(PairingRequest.ConfirmOnTv(pairingTimeoutMs))
                                }
                            }

                            else -> {
                                answer.complete(reply)
                            }
                        }
                    }
                } catch (e: IOException) {
                    // A TV that closes the handshake without an answer does not speak this
                    // protocol: a 2014–2015 model, which needs encrypted pairing.
                    val error = if (e is EOFException) UNSUPPORTED else TvError.Unreachable()
                    if (!answer.completeExceptionally(TvException(error, e)) && socket === source) {
                        socket = null
                        _state.value = ConnectionState.Failed(TvError.ConnectionLost())
                    }
                }
            }, "samsung-legacy-${device.host}")
        reader.isDaemon = true
        reader.start()
    }

    override fun supports(key: RemoteKey): Boolean = key.legacySamsungCode() != null

    /**
     * Sends [key]. The protocol only clicks, so a press is sent as a click and a release is
     * ignored; the session does not declare [Capability.LONG_PRESS].
     */
    override suspend fun send(
        key: RemoteKey,
        action: KeyAction,
    ) {
        val current = socket
        if (current == null || _state.value != ConnectionState.Connected) throw TvException(TvError.NotConnected)
        val code = key.legacySamsungCode() ?: return
        if (action == KeyAction.RELEASE) return
        withContext(Dispatchers.IO) {
            try {
                synchronized(current) { current.getOutputStream().write(LegacyProtocol.key(code)) }
            } catch (e: IOException) {
                throw TvException(TvError.ConnectionLost(), e)
            }
        }
    }

    override suspend fun disconnect() {
        val current = socket
        socket = null
        withContext(Dispatchers.IO) { runCatching { current?.close() } }
        _state.value = ConnectionState.Disconnected
    }

    private companion object {
        val UNSUPPORTED = TvError.Unsupported("this remote yet: Samsung TVs from 2014 and 2015 need a pairing Zapper does not have")
    }
}
