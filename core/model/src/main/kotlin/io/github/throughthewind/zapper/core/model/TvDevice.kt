package io.github.throughthewind.zapper.core.model

import kotlinx.serialization.Serializable

/**
 * A TV found on the local network or saved by the user.
 *
 * @property id stable identifier, unique per TV (for example a UPnP UDN or the MAC address).
 * @property adapterId which brand adapter handles this TV, for example `samsung`.
 */
@Serializable
data class TvDevice(
    val id: String,
    val name: String,
    val host: String,
    val adapterId: String,
    val model: String? = null,
    val macAddress: String? = null,
)
