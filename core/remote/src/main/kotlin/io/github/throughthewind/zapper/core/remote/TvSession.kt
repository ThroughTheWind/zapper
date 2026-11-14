package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A connection to one TV. */
interface TvSession {
    val device: TvDevice

    /** What this TV can do. May grow after connecting, once the model is known. */
    val capabilities: StateFlow<Set<Capability>>

    val state: StateFlow<ConnectionState>

    /** Connects, pairing first if the TV asks for it. Returns when connected; throws [TvException] otherwise. */
    suspend fun connect()

    /** True when this adapter knows how to send [key]. The UI disables keys that return false. */
    fun supports(key: RemoteKey): Boolean

    /** Sends [key]. A key [supports] rejects is ignored. Throws [TvException] when not connected. */
    suspend fun send(
        key: RemoteKey,
        action: KeyAction = KeyAction.CLICK,
    )

    /** Requires [Capability.APP_LAUNCH]. */
    suspend fun launchApp(appId: String): Unit = throw TvException(TvError.Unsupported("launching apps"))

    /**
     * True while the TV shows its on-screen keyboard, so text sent now lands in a field. Stays false
     * on a TV that does not say; [sendText] is still worth trying there.
     */
    val tvKeyboard: StateFlow<Boolean> get() = NO_TV_KEYBOARD

    /**
     * Types [text] into the TV's focused text field and confirms it, as Enter on the TV keyboard
     * would. Requires [Capability.TEXT_INPUT].
     */
    suspend fun sendText(text: String): Unit = throw TvException(TvError.Unsupported("text input"))

    suspend fun disconnect()
}

private val NO_TV_KEYBOARD: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    /** The TV needs the user to do something before it accepts the connection. */
    data class AwaitingPairing(
        val request: PairingRequest,
    ) : ConnectionState

    data object Connected : ConnectionState

    data class Failed(
        val error: TvError,
    ) : ConnectionState
}

/** What the user must do to pair. The pairing UI renders one screen per kind. */
sealed interface PairingRequest {
    /** Accept a prompt on the TV (Samsung, LG). */
    data class ConfirmOnTv(
        val timeoutMs: Long,
    ) : PairingRequest

    /** Type the code the TV shows (Android TV, Philips, Hisense). */
    data class EnterCode(
        val length: Int,
        val hex: Boolean,
        val submit: suspend (String) -> Unit,
    ) : PairingRequest
}

/** Why talking to a TV failed, in terms the UI can explain. */
sealed interface TvError {
    val message: String

    data class Unreachable(
        override val message: String = "The TV did not answer",
    ) : TvError

    data object PairingRefused : TvError {
        override val message = "The TV refused the connection"
    }

    data object PairingTimedOut : TvError {
        override val message = "Nobody answered the prompt on the TV"
    }

    /** A saved token stopped working; pairing must run again. */
    data object TokenRejected : TvError {
        override val message = "The TV no longer accepts this phone"
    }

    /** The TV presented a different certificate than when it was paired; pairing must run again. */
    data object CertificateChanged : TvError {
        override val message = "The TV's identity changed since it was paired"
    }

    data class ConnectionLost(
        override val message: String = "The connection to the TV was lost",
    ) : TvError

    data object NotConnected : TvError {
        override val message = "Not connected to a TV"
    }

    data class Unsupported(
        val feature: String,
    ) : TvError {
        override val message = "This TV does not support $feature"
    }
}

class TvException(
    val error: TvError,
    cause: Throwable? = null,
) : Exception(error.message, cause)

/** Gap between keys of a macro, so the TV does not drop presses. */
const val MACRO_KEY_GAP_MS: Long = 150

/** Runs what a layout button asks for on this session. */
suspend fun TvSession.perform(action: ButtonAction) {
    when (action) {
        is ButtonAction.Key -> {
            send(action.key)
        }

        is ButtonAction.LaunchApp -> {
            launchApp(action.appId)
        }

        is ButtonAction.Macro -> {
            action.keys.forEachIndexed { index, key ->
                if (index > 0) delay(MACRO_KEY_GAP_MS)
                send(key)
            }
        }

        ButtonAction.OpenKeyboard -> {
            // The phone keyboard is the app's to open; nothing goes to the TV until text is sent.
            Unit
        }
    }
}
