package io.github.throughthewind.zapper.adapters.roku

import io.github.throughthewind.zapper.core.model.Capability
import io.github.throughthewind.zapper.core.model.KeyAction
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.PowerState
import io.github.throughthewind.zapper.core.remote.TvApp
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import io.github.throughthewind.zapper.core.remote.TvSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One Roku. ECP has no connection and no pairing (research §5): connecting asks for the device
 * info and the app list, which also shows whether "Control by mobile apps" lets this phone in.
 * Each key is its own HTTP request, so a Roku that went away is noticed on the next key.
 */
internal class RokuSession(
    override val device: TvDevice,
    private val ecp: Ecp,
) : TvSession {
    override val capabilities: StateFlow<Set<Capability>> =
        MutableStateFlow(setOf(Capability.KEYS, Capability.LONG_PRESS, Capability.TEXT_INPUT, Capability.APP_LAUNCH)).asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** From the device info at connect; the saved device's until then. */
    @Volatile private var isTv: Boolean = device.properties[RokuDeviceInfo.PROPERTY_IS_TV] == "true"

    @Volatile private var apps: List<TvApp>? = null

    override suspend fun connect() {
        if (_state.value == ConnectionState.Connected) return
        _state.value = ConnectionState.Connecting
        try {
            val info =
                RokuDeviceInfo.parse(device.host, ecp.query("device-info")) ?: throw TvException(TvError.Unreachable("This is not a Roku"))
            isTv = info.properties[RokuDeviceInfo.PROPERTY_IS_TV] == "true"
            // Answers 403 when "Control by mobile apps" is Limited, where device-info may still answer.
            apps = parseApps(ecp.query("apps"))
        } catch (e: TvException) {
            _state.value = ConnectionState.Failed(e.error)
            throw e
        } catch (e: EcpStatusException) {
            val error = TvError.Unreachable(e.message!!)
            _state.value = ConnectionState.Failed(error)
            throw TvException(error, e)
        }
        _state.value = ConnectionState.Connected
    }

    override fun supports(key: RemoteKey): Boolean = if (key == RemoteKey.POWER) isTv else key.rokuKey(isTv) != null

    override suspend fun send(
        key: RemoteKey,
        action: KeyAction,
    ) {
        requireConnected()
        if (key == RemoteKey.POWER) {
            // A toggle on the phone; on/off on the TV. Release has nothing to do.
            if (isTv && action != KeyAction.RELEASE) key("keypress", powerKey())
            return
        }
        val name = key.rokuKey(isTv) ?: return
        val command =
            when (action) {
                KeyAction.CLICK -> "keypress"
                KeyAction.PRESS -> "keydown"
                KeyAction.RELEASE -> "keyup"
            }
        key(command, name)
    }

    /** `PowerOff` while the screen is on, otherwise `PowerOn`; `PowerOff` when the TV does not say. */
    private suspend fun powerKey(): String {
        val power = runCatching { RokuDeviceInfo.powerState(ecp.query("device-info")) }.getOrNull()
        return if (power == PowerState.STANDBY) RokuAdapter.POWER_ON else POWER_OFF
    }

    /** One `Lit_` keypress per character, then Enter (research §5). */
    override suspend fun sendText(text: String) {
        requireConnected()
        for (codePoint in text.codePoints().toArray()) key("keypress", rokuLiteral(codePoint))
        key("keypress", ROKU_ENTER)
    }

    override suspend fun installedApps(): List<TvApp> {
        requireConnected()
        apps?.let { return it }
        return try {
            parseApps(call { ecp.query("apps") }).also { apps = it }
        } catch (e: EcpStatusException) {
            emptyList()
        }
    }

    override suspend fun launchApp(appId: String) {
        val id = rokuAppId(appId)
        val installed = installedApps()
        if (installed.isNotEmpty() && installed.none { it.id == id }) throw TvException(TvError.AppNotInstalled)
        try {
            command("launch", id)
        } catch (e: EcpStatusException) {
            throw TvException(if (e.code == NOT_FOUND) TvError.AppNotInstalled else TvError.ConnectionLost(e.message!!), e)
        }
    }

    override suspend fun disconnect() {
        apps = null
        _state.value = ConnectionState.Disconnected
    }

    private fun requireConnected() {
        if (_state.value != ConnectionState.Connected) throw TvException(TvError.NotConnected)
    }

    private suspend fun command(
        command: String,
        argument: String,
    ) = call { ecp.command(command, argument) }

    /** Like [call], and an error status from the Roku fails this request only. */
    private suspend fun key(
        command: String,
        argument: String,
    ) = try {
        command(command, argument)
    } catch (e: EcpStatusException) {
        throw TvException(TvError.ConnectionLost(e.message!!), e)
    }

    /**
     * Runs one request. A Roku that stopped answering is a lost connection, which the app retries;
     * one that started refusing ends the session with the reason. [EcpStatusException] passes
     * through: the Roku is there, it only refused this one request.
     */
    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (e: TvException) {
            val error = if (e.error is TvError.Unreachable) TvError.ConnectionLost() else e.error
            _state.value = ConnectionState.Failed(error)
            throw TvException(error, e)
        }

    private companion object {
        const val POWER_OFF = "PowerOff"
        const val NOT_FOUND = 404
    }
}
