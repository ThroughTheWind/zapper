package io.github.throughthewind.zapper.adapters.samsung

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * The Samsung Tizen (2016+) remote control WebSocket API: messages in and out, with no I/O, so it
 * can be tested without a TV.
 */
internal object SamsungProtocol {
    const val SECURE_PORT = 8002
    private const val CHANNEL_PATH = "/api/v2/channels/samsung.remote.control"

    /** The URL to open. [appName] is what the TV shows in its "Allow this device?" prompt. */
    fun channelUrl(
        scheme: String,
        host: String,
        port: Int,
        appName: String,
        token: String?,
    ): String {
        val name = Base64.getEncoder().encodeToString(appName.toByteArray(Charsets.UTF_8))
        val tokenParam = token?.let { "&token=$it" } ?: ""
        return "$scheme://$host:$port$CHANNEL_PATH?name=$name$tokenParam"
    }

    /** [cmd] is `Click`, or `Press` then `Release` to hold a key (hold-to-repeat, long press). */
    fun keyPress(
        code: String,
        cmd: String = "Click",
    ): String =
        buildJsonObject {
            put("method", "ms.remote.control")
            putJsonObject("params") {
                put("Cmd", cmd)
                put("DataOfCmd", code)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            }
        }.toString()

    /**
     * Types [text] into the field the TV keyboard is editing. The TV only takes it while its
     * keyboard is on screen (research §2.3).
     */
    fun textInput(text: String): String =
        buildJsonObject {
            put("method", "ms.remote.control")
            putJsonObject("params") {
                put("Cmd", Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)))
                put("DataOfCmd", "base64")
                put("TypeOfRemote", "SendInputString")
            }
        }.toString()

    /** Confirms the typed text and closes the TV keyboard, like its Done key. */
    fun textInputEnd(): String =
        buildJsonObject {
            put("method", "ms.remote.control")
            putJsonObject("params") { put("TypeOfRemote", "SendInputEnd") }
        }.toString()

    /** What the TV said, reduced to what the session acts on. */
    sealed interface Event {
        /** The TV accepted us. [token] is set when it issued a new pairing token. */
        data class Connected(
            val token: String?,
        ) : Event

        /** The user refused the prompt on the TV, or the token was revoked. */
        data object Unauthorized : Event

        data object Timeout : Event

        /** The TV opened its on-screen keyboard: a text field wants input. */
        data object KeyboardShown : Event

        data object KeyboardHidden : Event

        data class Other(
            val name: String?,
        ) : Event
    }

    fun parse(message: String): Event {
        val json = Json.parseToJsonElement(message).jsonObject
        return when (val name = json["event"]?.jsonPrimitive?.contentOrNull) {
            "ms.channel.connect" -> {
                val token = (json["data"] as? JsonObject)?.get("token")?.jsonPrimitive?.contentOrNull
                Event.Connected(token)
            }

            "ms.channel.unauthorized" -> {
                Event.Unauthorized
            }

            "ms.channel.timeOut" -> {
                Event.Timeout
            }

            "ms.remote.imeStart" -> {
                Event.KeyboardShown
            }

            "ms.remote.imeEnd" -> {
                Event.KeyboardHidden
            }

            else -> {
                Event.Other(name)
            }
        }
    }
}
