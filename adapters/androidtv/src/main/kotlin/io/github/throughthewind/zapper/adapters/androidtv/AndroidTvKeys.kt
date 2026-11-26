package io.github.throughthewind.zapper.adapters.androidtv

import io.github.throughthewind.zapper.core.model.RemoteKey

/**
 * Android `KeyEvent` codes, which the protocol carries unchanged (research §3). They are Android's
 * public constants rather than reverse-engineered values, but none has been tried on a real TV yet:
 * the keys a box or TV may not handle (HDMI ports and Next HDMI, the channel list, Exit, the previous channel)
 * stay `null` until someone confirms them on a device.
 */
internal fun RemoteKey.androidTvCode(): Int? =
    when (this) {
        RemoteKey.POWER -> KEYCODE_POWER
        RemoteKey.HOME -> KEYCODE_HOME
        RemoteKey.BACK -> KEYCODE_BACK
        RemoteKey.EXIT -> null
        RemoteKey.MENU -> KEYCODE_MENU
        RemoteKey.SOURCE -> KEYCODE_TV_INPUT
        RemoteKey.HDMI_1, RemoteKey.HDMI_2, RemoteKey.HDMI_3, RemoteKey.HDMI_4, RemoteKey.HDMI_NEXT -> null
        RemoteKey.SETTINGS -> KEYCODE_SETTINGS
        RemoteKey.UP -> KEYCODE_DPAD_UP
        RemoteKey.DOWN -> KEYCODE_DPAD_DOWN
        RemoteKey.LEFT -> KEYCODE_DPAD_LEFT
        RemoteKey.RIGHT -> KEYCODE_DPAD_RIGHT
        RemoteKey.OK -> KEYCODE_DPAD_CENTER
        RemoteKey.VOLUME_UP -> KEYCODE_VOLUME_UP
        RemoteKey.VOLUME_DOWN -> KEYCODE_VOLUME_DOWN
        RemoteKey.MUTE -> KEYCODE_VOLUME_MUTE
        RemoteKey.CHANNEL_UP -> KEYCODE_CHANNEL_UP
        RemoteKey.CHANNEL_DOWN -> KEYCODE_CHANNEL_DOWN
        RemoteKey.CHANNEL_LIST -> null
        RemoteKey.PREVIOUS_CHANNEL -> null
        RemoteKey.PLAY -> KEYCODE_MEDIA_PLAY
        RemoteKey.PAUSE -> KEYCODE_MEDIA_PAUSE
        RemoteKey.PLAY_PAUSE -> KEYCODE_MEDIA_PLAY_PAUSE
        RemoteKey.STOP -> KEYCODE_MEDIA_STOP
        RemoteKey.REWIND -> KEYCODE_MEDIA_REWIND
        RemoteKey.FAST_FORWARD -> KEYCODE_MEDIA_FAST_FORWARD
        RemoteKey.DIGIT_0 -> KEYCODE_0
        RemoteKey.DIGIT_1 -> KEYCODE_1
        RemoteKey.DIGIT_2 -> KEYCODE_2
        RemoteKey.DIGIT_3 -> KEYCODE_3
        RemoteKey.DIGIT_4 -> KEYCODE_4
        RemoteKey.DIGIT_5 -> KEYCODE_5
        RemoteKey.DIGIT_6 -> KEYCODE_6
        RemoteKey.DIGIT_7 -> KEYCODE_7
        RemoteKey.DIGIT_8 -> KEYCODE_8
        RemoteKey.DIGIT_9 -> KEYCODE_9
        RemoteKey.INFO -> KEYCODE_INFO
        RemoteKey.GUIDE -> KEYCODE_GUIDE
        RemoteKey.RED -> KEYCODE_PROG_RED
        RemoteKey.GREEN -> KEYCODE_PROG_GREEN
        RemoteKey.YELLOW -> KEYCODE_PROG_YELLOW
        RemoteKey.BLUE -> KEYCODE_PROG_BLUE
    }

private const val KEYCODE_POWER = 26
private const val KEYCODE_HOME = 3
private const val KEYCODE_BACK = 4
private const val KEYCODE_MENU = 82
private const val KEYCODE_TV_INPUT = 178
private const val KEYCODE_SETTINGS = 176
private const val KEYCODE_DPAD_UP = 19
private const val KEYCODE_DPAD_DOWN = 20
private const val KEYCODE_DPAD_LEFT = 21
private const val KEYCODE_DPAD_RIGHT = 22
private const val KEYCODE_DPAD_CENTER = 23
private const val KEYCODE_VOLUME_UP = 24
private const val KEYCODE_VOLUME_DOWN = 25

// 91, KEYCODE_MUTE, is the microphone's.
private const val KEYCODE_VOLUME_MUTE = 164
private const val KEYCODE_CHANNEL_UP = 166
private const val KEYCODE_CHANNEL_DOWN = 167
private const val KEYCODE_MEDIA_PLAY = 126
private const val KEYCODE_MEDIA_PAUSE = 127
private const val KEYCODE_MEDIA_PLAY_PAUSE = 85
private const val KEYCODE_MEDIA_STOP = 86
private const val KEYCODE_MEDIA_REWIND = 89
private const val KEYCODE_MEDIA_FAST_FORWARD = 90
private const val KEYCODE_0 = 7
private const val KEYCODE_1 = 8
private const val KEYCODE_2 = 9
private const val KEYCODE_3 = 10
private const val KEYCODE_4 = 11
private const val KEYCODE_5 = 12
private const val KEYCODE_6 = 13
private const val KEYCODE_7 = 14
private const val KEYCODE_8 = 15
private const val KEYCODE_9 = 16
private const val KEYCODE_INFO = 165
private const val KEYCODE_GUIDE = 172
private const val KEYCODE_PROG_RED = 183
private const val KEYCODE_PROG_GREEN = 184
private const val KEYCODE_PROG_YELLOW = 185
private const val KEYCODE_PROG_BLUE = 186

/** KEYCODE_ENTER, which confirms the TV keyboard after text is typed. */
internal const val KEYCODE_ENTER = 66

/**
 * App links for each [io.github.throughthewind.zapper.core.model.KnownApps] entry. The protocol
 * has no app list, and since August 2026 `market://launch` links no longer open apps on some
 * devices, so deep links come first: these are the ones Home Assistant's integration documents.
 * The two without a documented deep link fall back to their package (research §3).
 */
internal val ANDROID_TV_APP_LINKS: Map<String, String> =
    mapOf(
        "netflix" to "netflix://",
        "youtube" to "https://www.youtube.com",
        "prime-video" to "https://app.primevideo.com",
        "disney-plus" to "https://www.disneyplus.com",
        "plex" to "plex://",
        "apple-tv" to marketLink("com.apple.atve.androidtv.appletv"),
        "spotify" to marketLink("com.spotify.tv.android"),
    )

/** The link to open [appId]: the table's, a link the caller gave, or a package name's launch link. */
internal fun androidTvAppLink(appId: String): String = ANDROID_TV_APP_LINKS[appId] ?: if ("://" in appId) appId else marketLink(appId)

private fun marketLink(packageName: String) = "market://launch?id=$packageName"
