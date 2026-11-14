package io.github.throughthewind.zapper.core.model

/**
 * A brand-neutral remote control key. The UI only ever sends these; translating one into a
 * protocol code is the job of a brand adapter (ADR-0002).
 */
enum class RemoteKey {
    POWER,
    HOME,
    BACK,
    EXIT,
    MENU,
    SOURCE,

    /**
     * Switches straight to an HDMI port. Not every TV has a key for this: an adapter that cannot
     * send one reports it unsupported, and the catalog hides it (FR-4.5).
     */
    HDMI_1,
    HDMI_2,
    HDMI_3,
    HDMI_4,
    SETTINGS,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    OK,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    CHANNEL_LIST,
    PREVIOUS_CHANNEL,
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    STOP,
    REWIND,
    FAST_FORWARD,
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9,
    INFO,
    GUIDE,
    RED,
    GREEN,
    YELLOW,
    BLUE,
}
