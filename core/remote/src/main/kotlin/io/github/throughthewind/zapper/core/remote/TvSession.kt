package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.ButtonAction
import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** A connection to one TV. */
interface TvSession {
    val device: TvDevice

    /** What this TV can do. The UI disables any control whose capability is missing. */
    val capabilities: Set<Capability>

    val state: StateFlow<ConnectionState>

    /** Connects, pairing first if the TV asks for it. Suspends until connected or failed. */
    suspend fun connect()

    suspend fun send(key: RemoteKey)

    /** Requires [Capability.APP_LAUNCH]. */
    suspend fun launchApp(appId: String): Unit = throw UnsupportedOperationException("${device.adapterId} cannot launch apps")

    /** Requires [Capability.TEXT_INPUT]. */
    suspend fun sendText(text: String): Unit = throw UnsupportedOperationException("${device.adapterId} cannot send text")

    suspend fun disconnect()
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    /** The TV is showing a prompt the user must accept, or a PIN to type. */
    data class AwaitingPairing(
        val pinRequired: Boolean,
    ) : ConnectionState

    data object Connected : ConnectionState

    data class Failed(
        val reason: String,
        val cause: Throwable? = null,
    ) : ConnectionState
}

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
    }
}
