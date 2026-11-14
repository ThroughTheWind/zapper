package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.decodeHtmlEntities
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The TV's self-description at `GET http://IP:8001/api/v2/`, which answers without pairing.
 * See docs/research/tv-remote-protocols.md §2.2.
 */
internal object SamsungDeviceInfo {
    const val INFO_PORT = 8001
    const val PROPERTY_TOKEN_AUTH = "tokenAuth"
    const val PROPERTY_POWER_STATE = "powerState"

    fun url(
        host: String,
        port: Int = INFO_PORT,
    ): String = "http://$host:$port/api/v2/"

    /** Null when [body] is not a Samsung Tizen TV's answer. */
    fun parse(
        host: String,
        body: String,
    ): DiscoveredTv? {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val device = root["device"] as? JsonObject ?: return null

        fun field(name: String) = device[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        val isTizen = field("OS") == "Tizen" || field("type")?.contains("Samsung", ignoreCase = true) == true
        if (!isTizen) return null
        val name =
            (
                field(
                    "name",
                ) ?: root["name"]?.jsonPrimitive?.contentOrNull ?: "Samsung TV"
            ).decodeHtmlEntities().removePrefix("[TV]").trim()
        return DiscoveredTv(
            adapterId = SamsungAdapter.ID,
            host = host,
            name = name,
            model = field("modelName"),
            macAddress = field("wifiMac")?.lowercase(),
            uniqueId = field("id") ?: field("duid"),
            properties =
                buildMap {
                    put(PROPERTY_TOKEN_AUTH, (field("TokenAuthSupport") ?: "true").lowercase())
                    field("PowerState")?.let { put(PROPERTY_POWER_STATE, it) }
                },
        )
    }
}
