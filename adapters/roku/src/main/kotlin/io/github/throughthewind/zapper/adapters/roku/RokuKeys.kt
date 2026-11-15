package io.github.throughthewind.zapper.adapters.roku

import io.github.throughthewind.zapper.core.model.RemoteKey

/**
 * ECP key names from Roku's documented list (research §5). A key Roku does not document maps to
 * null and is not sent. Power, inputs, channels and volume only exist on a Roku TV: a stick or box
 * has no tuner, no HDMI inputs and no speaker of its own, so [isTv] false leaves them out.
 *
 * [RemoteKey.POWER] is not here: Roku has `PowerOn` and `PowerOff` rather than a toggle, so the
 * session picks one from the TV's power mode.
 */
internal fun RemoteKey.rokuKey(isTv: Boolean): String? =
    when (this) {
        RemoteKey.HOME -> "Home"

        RemoteKey.BACK -> "Back"

        RemoteKey.UP -> "Up"

        RemoteKey.DOWN -> "Down"

        RemoteKey.LEFT -> "Left"

        RemoteKey.RIGHT -> "Right"

        RemoteKey.OK -> "Select"

        // Roku's ✱ key, which opens the options of what is on screen.
        RemoteKey.INFO -> "Info"

        // Roku's Play is play/pause.
        RemoteKey.PLAY_PAUSE -> "Play"

        RemoteKey.REWIND -> "Rev"

        RemoteKey.FAST_FORWARD -> "Fwd"

        RemoteKey.VOLUME_UP -> "VolumeUp".takeIf { isTv }

        RemoteKey.VOLUME_DOWN -> "VolumeDown".takeIf { isTv }

        RemoteKey.MUTE -> "VolumeMute".takeIf { isTv }

        RemoteKey.CHANNEL_UP -> "ChannelUp".takeIf { isTv }

        RemoteKey.CHANNEL_DOWN -> "ChannelDown".takeIf { isTv }

        RemoteKey.HDMI_1 -> "InputHDMI1".takeIf { isTv }

        RemoteKey.HDMI_2 -> "InputHDMI2".takeIf { isTv }

        RemoteKey.HDMI_3 -> "InputHDMI3".takeIf { isTv }

        RemoteKey.HDMI_4 -> "InputHDMI4".takeIf { isTv }

        RemoteKey.POWER,
        RemoteKey.EXIT,
        RemoteKey.MENU,
        RemoteKey.SOURCE,
        RemoteKey.SETTINGS,
        RemoteKey.CHANNEL_LIST,
        RemoteKey.PREVIOUS_CHANNEL,
        RemoteKey.PLAY,
        RemoteKey.PAUSE,
        RemoteKey.STOP,
        RemoteKey.DIGIT_0,
        RemoteKey.DIGIT_1,
        RemoteKey.DIGIT_2,
        RemoteKey.DIGIT_3,
        RemoteKey.DIGIT_4,
        RemoteKey.DIGIT_5,
        RemoteKey.DIGIT_6,
        RemoteKey.DIGIT_7,
        RemoteKey.DIGIT_8,
        RemoteKey.DIGIT_9,
        RemoteKey.GUIDE,
        RemoteKey.RED,
        RemoteKey.GREEN,
        RemoteKey.YELLOW,
        RemoteKey.BLUE,
        -> null
    }

/** Key used after typed text, as the TV keyboard's own Enter. */
internal const val ROKU_ENTER = "Enter"

/** Roku types text one character at a time as `Lit_<char>`. */
internal fun rokuLiteral(codePoint: Int): String = "Lit_" + Ecp.encodeSegment(String(Character.toChars(codePoint)))
