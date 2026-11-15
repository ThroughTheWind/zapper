package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.KnownApps
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
    fun `apps are listed and launched over ms channel emit`() {
        val list = Json.parseToJsonElement(SamsungProtocol.installedAppsRequest()).jsonObject
        assertEquals("ms.channel.emit", list["method"]!!.jsonPrimitive.content)
        assertEquals("ed.installedApp.get", list["params"]!!.jsonObject["event"]!!.jsonPrimitive.content)

        val launch = Json.parseToJsonElement(SamsungProtocol.launchApp("3201907018807")).jsonObject["params"]!!.jsonObject
        assertEquals("ed.apps.launch", launch["event"]!!.jsonPrimitive.content)
        val data = launch["data"]!!.jsonObject
        assertEquals("3201907018807", data["appId"]!!.jsonPrimitive.content)
        assertEquals("DEEP_LINK", data["action_type"]!!.jsonPrimitive.content)
        assertEquals(
            "NATIVE_LAUNCH",
            Json
                .parseToJsonElement(SamsungProtocol.launchApp("x", native = true))
                .jsonObject["params"]!!
                .jsonObject["data"]!!
                .jsonObject["action_type"]!!
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `the installed app list is parsed, native apps marked`() {
        val event =
            SamsungProtocol.parse(
                """{"event":"ed.installedApp.get","from":"host","data":{"data":[
                    {"appId":"11101200001","app_type":2,"name":"Netflix","icon":"/opt/x.png"},
                    {"appId":"org.tizen.browser","app_type":4,"name":"Internet"},
                    {"name":"no id"}
                ]}}""",
            )

        assertEquals(
            SamsungProtocol.Event.InstalledApps(
                listOf(
                    SamsungProtocol.App("11101200001", "Netflix", native = false),
                    SamsungProtocol.App("org.tizen.browser", "Internet", native = true),
                ),
            ),
            event,
        )
    }

    @Test
    fun `every known app has a Samsung id`() {
        KnownApps.all.forEach { assertTrue(SAMSUNG_APP_IDS[it.id].orEmpty().isNotEmpty(), it.id) }
        assertEquals(listOf("org.tizen.browser"), samsungAppIds("org.tizen.browser"))
    }

    @Test
    fun `source and HDMI keys map to the Samsung input codes`() {
        assertEquals("KEY_SOURCE", RemoteKey.SOURCE.samsungCode())
        assertEquals(
            listOf("KEY_HDMI1", "KEY_HDMI2", "KEY_HDMI3", "KEY_HDMI4"),
            listOf(RemoteKey.HDMI_1, RemoteKey.HDMI_2, RemoteKey.HDMI_3, RemoteKey.HDMI_4).map { it.samsungCode() },
        )
    }

    @Test
    fun `every mapped key code follows the KEY_ convention`() {
        RemoteKey.entries.mapNotNull { it.samsungCode() }.forEach { assertTrue(it.startsWith("KEY_"), it) }
    }
}
