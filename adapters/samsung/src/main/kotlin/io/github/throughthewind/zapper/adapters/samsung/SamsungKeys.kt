package io.github.throughthewind.zapper.adapters.samsung

import io.github.throughthewind.zapper.core.model.RemoteKey

/**
 * Samsung Tizen key codes. A key mapped to null is not sent: the code is not confirmed yet, and a
 * wrong code fails silently on the TV. The protocol research confirms or fills these in.
 *
 * The direct HDMI keys are not on every model (research, Samsung key list). A TV without them
 * ignores the key, and the local channel cannot tell which models have them.
 */
internal fun RemoteKey.samsungCode(): String? =
    when (this) {
        RemoteKey.POWER -> "KEY_POWER"
        RemoteKey.HOME -> "KEY_HOME"
        RemoteKey.BACK -> "KEY_RETURN"
        RemoteKey.EXIT -> "KEY_EXIT"
        RemoteKey.MENU -> "KEY_MENU"
        RemoteKey.SOURCE -> "KEY_SOURCE"
        RemoteKey.HDMI_1 -> "KEY_HDMI1"
        RemoteKey.HDMI_2 -> "KEY_HDMI2"
        RemoteKey.HDMI_3 -> "KEY_HDMI3"
        RemoteKey.HDMI_4 -> "KEY_HDMI4"
        RemoteKey.SETTINGS -> null
        RemoteKey.UP -> "KEY_UP"
        RemoteKey.DOWN -> "KEY_DOWN"
        RemoteKey.LEFT -> "KEY_LEFT"
        RemoteKey.RIGHT -> "KEY_RIGHT"
        RemoteKey.OK -> "KEY_ENTER"
        RemoteKey.VOLUME_UP -> "KEY_VOLUP"
        RemoteKey.VOLUME_DOWN -> "KEY_VOLDOWN"
        RemoteKey.MUTE -> "KEY_MUTE"
        RemoteKey.CHANNEL_UP -> "KEY_CHUP"
        RemoteKey.CHANNEL_DOWN -> "KEY_CHDOWN"
        RemoteKey.CHANNEL_LIST -> "KEY_CH_LIST"
        RemoteKey.PREVIOUS_CHANNEL -> "KEY_PRECH"
        RemoteKey.PLAY -> "KEY_PLAY"
        RemoteKey.PAUSE -> "KEY_PAUSE"
        RemoteKey.PLAY_PAUSE -> null
        RemoteKey.STOP -> "KEY_STOP"
        RemoteKey.REWIND -> "KEY_REWIND"
        RemoteKey.FAST_FORWARD -> "KEY_FF"
        RemoteKey.DIGIT_0 -> "KEY_0"
        RemoteKey.DIGIT_1 -> "KEY_1"
        RemoteKey.DIGIT_2 -> "KEY_2"
        RemoteKey.DIGIT_3 -> "KEY_3"
        RemoteKey.DIGIT_4 -> "KEY_4"
        RemoteKey.DIGIT_5 -> "KEY_5"
        RemoteKey.DIGIT_6 -> "KEY_6"
        RemoteKey.DIGIT_7 -> "KEY_7"
        RemoteKey.DIGIT_8 -> "KEY_8"
        RemoteKey.DIGIT_9 -> "KEY_9"
        RemoteKey.INFO -> "KEY_INFO"
        RemoteKey.GUIDE -> "KEY_GUIDE"
        RemoteKey.RED -> "KEY_RED"
        RemoteKey.GREEN -> "KEY_GREEN"
        RemoteKey.YELLOW -> "KEY_YELLOW"
        RemoteKey.BLUE -> "KEY_CYAN"
    }
