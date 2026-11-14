package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.RemoteKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SamsungProtocolTest {
    @Test
    fun `the channel URL carries the app name in base64 and the token when there is one`() {
        assertEquals(
            "wss://192.168.1.20:8002/api/v2/channels/samsung.remote.control?name=WmFwcGVy&token=12345",
            SamsungProtocol.channelUrl("wss", "192.168.1.20", 8002, "Zapper", "12345"),
        )
        assertEquals(
            "wss://192.168.1.20:8002/api/v2/channels/samsung.remote.control?name=WmFwcGVy",
            SamsungProtocol.channelUrl("wss", "192.168.1.20", 8002, "Zapper", null),
        )
    }

    @Test
    fun `a key press is a Click of SendRemoteKey`() {
        val params =
            Json
                .parseToJsonElement(SamsungProtocol.keyPress("KEY_VOLUP"))
                .jsonObject["params"]!!
                .jsonObject

        assertEquals("Click", params["Cmd"]!!.jsonPrimitive.content)
        assertEquals("KEY_VOLUP", params["DataOfCmd"]!!.jsonPrimitive.content)
        assertEquals("SendRemoteKey", params["TypeOfRemote"]!!.jsonPrimitive.content)
    }

    @Test
    fun `connect events carry the pairing token`() {
        val event = SamsungProtocol.parse("""{"event":"ms.channel.connect","data":{"id":"x","token":"98765"}}""")

        assertEquals(SamsungProtocol.Event.Connected("98765"), event)
    }

    @Test
    fun `a refused prompt is unauthorized`() {
        assertEquals(SamsungProtocol.Event.Unauthorized, SamsungProtocol.parse("""{"event":"ms.channel.unauthorized"}"""))
    }

    @Test
    fun `text goes as base64 in SendInputString, then SendInputEnd confirms it`() {
        val params =
            Json
                .parseToJsonElement(SamsungProtocol.textInput("stranger"))
                .jsonObject["params"]!!
                .jsonObject
        assertEquals("c3RyYW5nZXI=", params["Cmd"]!!.jsonPrimitive.content)
        assertEquals("base64", params["DataOfCmd"]!!.jsonPrimitive.content)
        assertEquals("SendInputString", params["TypeOfRemote"]!!.jsonPrimitive.content)

        val end =
            Json
                .parseToJsonElement(SamsungProtocol.textInputEnd())
                .jsonObject["params"]!!
                .jsonObject
        assertEquals("SendInputEnd", end["TypeOfRemote"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the TV keyboard opening and closing are events`() {
        assertEquals(SamsungProtocol.Event.KeyboardShown, SamsungProtocol.parse("""{"event":"ms.remote.imeStart","data":""}"""))
        assertEquals(SamsungProtocol.Event.KeyboardHidden, SamsungProtocol.parse("""{"event":"ms.remote.imeEnd"}"""))
    }

    @Test
    fun `every mapped key code follows the KEY_ convention`() {
        RemoteKey.entries.mapNotNull { it.samsungCode() }.forEach { assertTrue(it.startsWith("KEY_"), it) }
    }
}
