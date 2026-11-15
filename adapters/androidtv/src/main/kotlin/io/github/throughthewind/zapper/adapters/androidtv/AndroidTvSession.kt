package io.github.throughthewind.zapper.adapters.androidtv

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.OutputStream
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/**
 * One Android TV or Google TV over the Android TV Remote protocol v2 (research §3).
 *
 * The first connection pairs on [pairingPort]: the phone makes a key and certificate, the TV shows
 * a 6-character code, and the code proves both sides saw the same keys. The certificate is then the
 * token: the remote port lets it in from then on. When the TV forgets it, the TLS handshake fails
 * and the session reports [TvError.TokenRejected], so pairing runs again.
 */
internal class AndroidTvSession(
    override val device: TvDevice,
    private val credentials: CredentialStore,
    private val clientName: String,
    private val remotePort: Int = Remote.PORT,
    private val pairingPort: Int = Polo.PORT,
    private val connectTimeoutMs: Int = 5_000,
    private val replyTimeoutMs: Long = 10_000,
    private val pairingTimeoutMs: Long = 120_000,
    /** The TV pings every 5 s and drops a client after three unanswered; silence this long is a lost link. */
    private val idleTimeoutMs: Int = 16_000,
    private val newIdentity: () -> ClientIdentity = { ClientIdentity.generate(clientName) },
) : TvSession {
    override val capabilities: StateFlow<Set<Capability>> =
        MutableStateFlow(setOf(Capability.KEYS, Capability.LONG_PRESS, Capability.TEXT_INPUT, Capability.APP_LAUNCH)).asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile private var socket: SSLSocket? = null

    @Volatile private var scope: CoroutineScope? = null

    private val writeLock = Any()

    // The focused text field, as the TV last described it; text edits must quote both counters.
    @Volatile private var imeCounter = 0L

    @Volatile private var fieldCounter = 0L

    override suspend fun connect() {
        if (_state.value == ConnectionState.Connected) return
        _state.value = ConnectionState.Connecting
        try {
            val saved = credentials.token(device.id)?.let { ClientIdentity.decode(it) }
            val identity = saved ?: pair()
            openRemote(identity, saved = saved != null)
            _state.value = ConnectionState.Connected
        } catch (e: CancellationException) {
            close()
            _state.value = ConnectionState.Disconnected
            throw e
        } catch (e: Exception) {
            close()
            val error = e as? TvException ?: TvException(TvError.Unreachable(), e)
            _state.value = ConnectionState.Failed(error.error)
            throw error
        }
    }

    /** Runs the pairing exchange, waits for the user to type the code, and saves the new identity. */
    private suspend fun pair(): ClientIdentity {
        val identity = withContext(Dispatchers.Default) { newIdentity() }
        val pairing =
            withContext(Dispatchers.IO) {
                try {
                    AndroidTvTls.connect(device.host, pairingPort, connectTimeoutMs, PinningTrustManager(null)) { identity }
                } catch (e: IOException) {
                    throw TvException(TvError.Unreachable(), e)
                }
            }
        try {
            pairing.soTimeout = replyTimeoutMs.toInt()
            val serverKey =
                pairing.session.peerCertificates
                    .first()
                    .publicKey as? RSAPublicKey ?: throw TvException(TvError.PairingRefused)
            withContext(Dispatchers.IO) {
                exchange(pairing, Polo.pairingRequest(clientName), Polo.PAIRING_REQUEST_ACK)
                exchange(pairing, Polo.options(), Polo.OPTIONS)
                exchange(pairing, Polo.configuration(), Polo.CONFIGURATION_ACK)
            }

            // The TV now shows the code.
            val paired = CompletableDeferred<Unit>()
            val submitting = Mutex()
            val request =
                PairingRequest.EnterCode(length = Polo.CODE_LENGTH, hex = true) { code ->
                    submitting.withLock {
                        if (paired.isCompleted) return@withLock
                        // A typo is caught locally and the TV keeps its code up, so the user can retype.
                        val secret = Polo.secretFor(identity.publicKey, serverKey, code) ?: throw TvException(TvError.PairingRefused)
                        try {
                            withContext(Dispatchers.IO) { exchange(pairing, Polo.secret(secret), Polo.SECRET_ACK) }
                            paired.complete(Unit)
                        } catch (e: TvException) {
                            paired.completeExceptionally(e)
                            throw e
                        }
                    }
                }
            _state.value = ConnectionState.AwaitingPairing(request)
            try {
                withTimeout(pairingTimeoutMs) { paired.await() }
            } catch (e: TimeoutCancellationException) {
                throw TvException(TvError.PairingTimedOut, e)
            }
            credentials.saveToken(device.id, identity.encode())
            _state.value = ConnectionState.Connecting
            return identity
        } finally {
            withContext(Dispatchers.IO) { runCatching { pairing.close() } }
        }
    }

    /** Sends one pairing message and reads the TV's answer, which must carry [expected]. */
    private fun exchange(
        pairing: SSLSocket,
        message: ByteArray,
        expected: Int,
    ) {
        val reply =
            try {
                pairing.outputStream.apply { write(ProtoFrames.frame(message)) }.flush()
                ProtoFrames.read(pairing.inputStream)
            } catch (e: SocketTimeoutException) {
                throw TvException(TvError.Unreachable("The TV stopped answering while pairing"), e)
            } catch (e: IOException) {
                throw TvException(TvError.PairingRefused, e)
            }
        // A wrong code is STATUS_BAD_SECRET; any other failure status also means the TV said no.
        if (reply.int(2) != Polo.STATUS_OK || !reply.has(expected)) throw TvException(TvError.PairingRefused)
    }

    /** Opens the remote port and waits for the TV's `RemoteStart`, which means it takes keys. */
    private suspend fun openRemote(
        identity: ClientIdentity,
        saved: Boolean,
    ) {
        val pin = credentials.read(device.id, CERT_PIN)
        val trust = PinningTrustManager(pin)
        // A TV that no longer knows the certificate fails the handshake, or with TLS 1.3 right after it.
        val refused = if (saved) TvError.TokenRejected else TvError.PairingRefused
        val remote =
            withContext(Dispatchers.IO) {
                try {
                    AndroidTvTls.connect(device.host, remotePort, connectTimeoutMs, trust) { identity }
                } catch (e: HandshakeFailed) {
                    val error =
                        when {
                            trust.mismatch -> TvError.CertificateChanged
                            e.cause is SocketTimeoutException -> TvError.Unreachable()
                            else -> refused
                        }
                    throw TvException(error, e)
                } catch (e: IOException) {
                    throw TvException(TvError.Unreachable(), e)
                }
            }
        remote.soTimeout = idleTimeoutMs
        socket = remote
        val ready = CompletableDeferred<Unit>()
        val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = connectionScope
        connectionScope.launch { readLoop(remote, ready, refused) }
        try {
            withTimeout(replyTimeoutMs) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            throw TvException(TvError.Unreachable("The TV did not start the remote"), e)
        }
        if (pin == null) trust.seen?.let { credentials.write(device.id, CERT_PIN, AndroidTvTls.fingerprint(it)) }
    }

    private fun readLoop(
        remote: SSLSocket,
        ready: CompletableDeferred<Unit>,
        refused: TvError,
    ) {
        try {
            val input = remote.inputStream
            while (true) handle(remote.outputStream, ProtoFrames.read(input), ready)
        } catch (e: Exception) {
            // Before the TV says it started, a TLS error or a reset is the certificate being refused (TLS 1.3 refuses after the handshake).
            val error =
                if (e is SSLException || e is SocketException) {
                    refused
                } else {
                    TvError.ConnectionLost()
                }
            if (!ready.completeExceptionally(TvException(error, e)) && socket === remote) {
                close()
                _state.value = ConnectionState.Failed(TvError.ConnectionLost())
            }
        }
    }

    private fun handle(
        output: OutputStream,
        message: ProtoMessage,
        ready: CompletableDeferred<Unit>,
    ) {
        when {
            message.has(Remote.PING_REQUEST) -> {
                write(output, Remote.pingResponse(message.message(Remote.PING_REQUEST)?.int(1) ?: 0))
            }

            message.has(Remote.CONFIGURE) -> {
                val supported = message.message(Remote.CONFIGURE)?.int(1)?.toInt() ?: 0
                write(output, Remote.configure(Remote.WANTED_FEATURES and supported))
            }

            message.has(Remote.SET_ACTIVE) -> {
                write(output, Remote.setActive(Remote.WANTED_FEATURES))
            }

            message.has(Remote.START) -> {
                ready.complete(Unit)
            }

            message.has(Remote.IME_KEY_INJECT) -> {
                val inject = message.message(Remote.IME_KEY_INJECT)
                inject?.message(1)?.int(1)?.let { imeCounter = it }
                inject?.message(2)?.int(1)?.let { fieldCounter = it }
            }

            message.has(Remote.IME_SHOW_REQUEST) -> {
                message
                    .message(Remote.IME_SHOW_REQUEST)
                    ?.message(2)
                    ?.int(1)
                    ?.let { fieldCounter = it }
            }

            message.has(Remote.IME_BATCH_EDIT) -> {
                val edit = message.message(Remote.IME_BATCH_EDIT)
                if (imeCounter == 0L) edit?.int(1)?.let { imeCounter = it }
                if (fieldCounter == 0L) edit?.int(2)?.let { fieldCounter = it }
            }

            // Volume, errors and anything newer: nothing the UI shows yet.
            else -> {
                Unit
            }
        }
    }

    private fun write(
        output: OutputStream,
        message: ByteArray,
    ) = synchronized(writeLock) {
        output.write(ProtoFrames.frame(message))
        output.flush()
    }

    override fun supports(key: RemoteKey): Boolean = key.androidTvCode() != null

    override suspend fun send(
        key: RemoteKey,
        action: KeyAction,
    ) {
        val remote = connectedSocket()
        val code = key.androidTvCode() ?: return
        val direction =
            when (action) {
                KeyAction.CLICK -> Remote.SHORT
                KeyAction.PRESS -> Remote.START_LONG
                KeyAction.RELEASE -> Remote.END_LONG
            }
        sendTo(remote, Remote.key(code, direction))
    }

    /** Inserts [text] at the cursor of the TV's focused field, then presses Enter to confirm it. */
    override suspend fun sendText(text: String) {
        val remote = connectedSocket()
        if (text.isNotEmpty()) sendTo(remote, Remote.imeText(imeCounter, fieldCounter, text))
        sendTo(remote, Remote.key(KEYCODE_ENTER, Remote.SHORT))
    }

    override suspend fun launchApp(appId: String) = sendTo(connectedSocket(), Remote.appLink(androidTvAppLink(appId)))

    private suspend fun sendTo(
        remote: SSLSocket,
        message: ByteArray,
    ) = withContext(Dispatchers.IO) {
        try {
            write(remote.outputStream, message)
        } catch (e: IOException) {
            throw TvException(TvError.ConnectionLost(), e)
        }
    }

    private fun connectedSocket(): SSLSocket {
        val remote = socket
        if (remote == null || _state.value != ConnectionState.Connected) throw TvException(TvError.NotConnected)
        return remote
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) { close() }
        _state.value = ConnectionState.Disconnected
    }

    private fun close() {
        val remote = socket
        socket = null
        scope?.cancel()
        scope = null
        imeCounter = 0
        fieldCounter = 0
        runCatching { remote?.close() }
    }

    private companion object {
        const val CERT_PIN = "certPin"
    }
}
