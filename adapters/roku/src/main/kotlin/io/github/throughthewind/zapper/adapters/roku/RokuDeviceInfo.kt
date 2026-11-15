package io.github.throughthewind.zapper.adapters.roku

import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import io.github.throughthewind.zapper.core.remote.PowerState

/**
 * The Roku's self-description at `GET http://IP:8060/query/device-info`, which answers without
 * pairing (research §5).
 */
internal object RokuDeviceInfo {
    /** `true` on a Roku TV (TCL, Hisense, Sharp…), `false` on a stick or box, which has no power, inputs or tuner. */
    const val PROPERTY_IS_TV = "isTv"

    /** Null when [body] is not a Roku's answer. */
    fun parse(
        host: String,
        body: String,
    ): DiscoveredTv? {
        if (!body.contains("<device-info")) return null
        val fields = Ecp.fields(body)

        fun field(name: String) = fields[name]?.takeIf { it.isNotBlank() }

        val isRoku = field("vendor-name")?.contains("Roku", ignoreCase = true) == true || field("udn") != null
        if (!isRoku) return null
        val isTv = field("is-tv")?.lowercase() == "true"
        val mac = if (field("network-type") == "ethernet") field("ethernet-mac") else field("wifi-mac")
        return DiscoveredTv(
            adapterId = RokuAdapter.ID,
            host = host,
            name =
                field("user-device-name")
                    ?: field("friendly-device-name")
                    ?: field("default-device-name")
                    ?: if (isTv) "Roku TV" else "Roku",
            // A TV by its maker's model ("TCL 55S435"), a player by Roku's name for it ("Roku Express").
            model =
                if (isTv) {
                    listOfNotNull(field("vendor-name"), field("model-name")).joinToString(" ").ifBlank { null }
                } else {
                    field("friendly-model-name") ?: field("model-name")
                },
            macAddress = mac?.lowercase(),
            uniqueId = field("udn")?.let { "uuid:$it" } ?: field("serial-number")?.let { "roku:$it" },
            properties = mapOf(PROPERTY_IS_TV to isTv.toString()),
        )
    }

    /**
     * `power-mode` is `PowerOn` while the screen is on; `DisplayOff`, `Ready` or `Headless` in
     * standby with Fast TV start. A stick has no power to report, so it gives null.
     */
    fun powerState(body: String): PowerState? {
        val fields = Ecp.fields(body)
        if (fields["is-tv"]?.lowercase() != "true") return null
        return when (fields["power-mode"]) {
            null, "" -> null
            "PowerOn" -> PowerState.ON
            else -> PowerState.STANDBY
        }
    }
}
