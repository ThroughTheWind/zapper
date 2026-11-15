package io.github.throughthewind.zapper.adapters.roku

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A Roku on localhost that answers ECP as Roku documents it: device-info and apps as XML, and
 * every `POST` command recorded by its raw path, such as `keypress/Lit_%2B`.
 */
class FakeRoku(
    isTv: Boolean = true,
) : AutoCloseable {
    val server = MockWebServer()

    /** Commands received, in order, without the leading slash. */
    val commands = CopyOnWriteArrayList<String>()

    @Volatile var deviceInfo: String = if (isTv) TCL_TV else STREAMING_STICK

    @Volatile var apps: String = APPS

    /** What `power-mode` says; only a TV reports it. */
    @Volatile var powerMode: String = "PowerOn"

    /** "Control by mobile apps" set to Limited: every request but device-info answers 403. */
    @Volatile var limited: Boolean = false

    val port: Int get() = server.port

    fun start() = apply { server.start() }

    init {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath.removePrefix("/")
                    if (request.method == "GET" && path == "query/device-info") {
                        return ok(deviceInfo.replace("{power}", powerMode))
                    }
                    if (limited) return MockResponse.Builder().code(403).build()
                    return when {
                        request.method == "GET" && path == "query/apps" -> {
                            ok(apps)
                        }

                        request.method == "POST" && path.startsWith("launch/") && path.removePrefix("launch/") !in installedIds() -> {
                            MockResponse.Builder().code(404).build()
                        }

                        request.method == "POST" -> {
                            commands += path
                            ok("")
                        }

                        else -> {
                            MockResponse.Builder().code(404).build()
                        }
                    }
                }
            }
    }

    private fun installedIds() = Regex("""id="([^"]+)"""").findAll(apps).map { it.groupValues[1] }.toSet()

    private fun ok(body: String) =
        MockResponse
            .Builder()
            .code(200)
            .body(body)
            .build()

    override fun close() = server.close()

    companion object {
        /** Shaped like a 2021 TCL Roku TV's answer, values made up. */
        val TCL_TV =
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
            	<udn>29780015-2c06-1066-80c9-d4e22fbc5c8a</udn>
            	<serial-number>X00000ABCDEF</serial-number>
            	<device-id>S00000ABCDEF</device-id>
            	<vendor-name>TCL</vendor-name>
            	<model-name>55S435</model-name>
            	<model-number>7105X</model-number>
            	<friendly-device-name>Living room &amp; kitchen</friendly-device-name>
            	<friendly-model-name>TCL•Roku TV</friendly-model-name>
            	<default-device-name>TCL•Roku TV - X00000ABCDEF</default-device-name>
            	<user-device-name>Living room &amp; kitchen</user-device-name>
            	<is-tv>true</is-tv>
            	<is-stick>false</is-stick>
            	<wifi-mac>d4:e2:2f:bc:5c:8a</wifi-mac>
            	<ethernet-mac>D4:E2:2F:BC:5C:8B</ethernet-mac>
            	<network-type>ethernet</network-type>
            	<power-mode>{power}</power-mode>
            	<supports-wake-on-wlan>false</supports-wake-on-wlan>
            </device-info>
            """.trimIndent()

        /** Shaped like a Roku Express's answer: no TV, no MAC on Wi-Fi when Limited. */
        val STREAMING_STICK =
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
            	<udn>015e5108-9000-1046-8035-b0a737964dfb</udn>
            	<serial-number>YH00AB000000</serial-number>
            	<vendor-name>Roku</vendor-name>
            	<model-name>Roku Express</model-name>
            	<friendly-model-name>Roku Express</friendly-model-name>
            	<default-device-name>Roku Express - YH00AB000000</default-device-name>
            	<is-tv>false</is-tv>
            	<is-stick>false</is-stick>
            	<wifi-mac>b0:a7:37:96:4d:fb</wifi-mac>
            	<network-type>wifi</network-type>
            	<power-mode>PowerOn</power-mode>
            </device-info>
            """.trimIndent()

        val APPS =
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <apps>
            	<app id="tvinput.hdmi1" type="tvin" version="1.0.0">HDMI 1</app>
            	<app id="12" type="appl" version="5.1.120079028">Netflix</app>
            	<app id="837" type="appl" version="2.21.100100031">YouTube</app>
            	<app id="2213" type="appl" version="4.2.1">Roku Media Player</app>
            	<app id="151908" type="appl" version="3.4.0">The Roku Channel &amp; more</app>
            	<app id="tvinput.dtv" type="tvin" version="1.0.0">Live TV</app>
            </apps>
            """.trimIndent()
    }
}
