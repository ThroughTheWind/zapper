package io.github.throughthewind.zapper.core.model

/**
 * What a connected TV can do. The UI shows, hides or disables controls from these, never from
 * the brand (ADR-0002).
 */
enum class Capability {
    /** Sends [RemoteKey] presses. Every adapter supports this. */
    KEYS,

    /** Holds a key down and releases it later ([KeyAction.PRESS] then [KeyAction.RELEASE]). */
    LONG_PRESS,

    /** Types text into the TV's focused text field. */
    TEXT_INPUT,

    /** Lists installed apps and launches one. */
    APP_LAUNCH,

    /** Turns the TV on from standby, for example with Wake-on-LAN. */
    POWER_ON,

    /** Moves an on-screen pointer like a touchpad. */
    POINTER,
}

/** How a key is sent. [PRESS] and [RELEASE] need [Capability.LONG_PRESS]. */
enum class KeyAction { CLICK, PRESS, RELEASE }
