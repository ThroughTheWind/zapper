package io.github.throughthewind.zapper.core.model

import kotlinx.serialization.Serializable

/** Light, dark or following the phone (SPEC FR-9.1). */
@Serializable
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Dynamic colour from the wallpaper (Android 12+) or one of the design system's six seeds. */
@Serializable
enum class ColorChoice { DYNAMIC, INDIGO, TEAL, AMBER, ROSE, GREEN, GRAPHITE }

/** The shape of 1x1 keys (docs/design/theming-and-customisation). */
@Serializable
enum class KeyShapeChoice { ROUNDED, SQUIRCLE, CIRCLE, SQUARE }

/** Haptic on press (SPEC FR-10.1). */
@Serializable
enum class HapticStrength { OFF, LIGHT, STRONG }

/**
 * Everything Settings > Appearance, Feedback and Controls change (SPEC F9, F10, Settings screen).
 * Stored as JSON: every field keeps a default so older files still read.
 */
@Serializable
data class AppearanceSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** Pure black backgrounds when dark (FR-9.2). */
    val oledBlack: Boolean = false,
    val color: ColorChoice = ColorChoice.DYNAMIC,
    val keyShape: KeyShapeChoice = KeyShapeChoice.ROUNDED,
    val haptics: HapticStrength = HapticStrength.LIGHT,
    /** A click sound on press, off by default (FR-10.2). */
    val clickSound: Boolean = false,
    /** Hold-to-repeat speed, 1 (slow) to 5 (fast) (SettingsControls). */
    val repeatSpeed: Int = DEFAULT_REPEAT_SPEED,
) {
    /** The time between repeats of a held key, from 150 ms (slow) to 45 ms (fast). */
    val repeatIntervalMs: Long get() = REPEAT_INTERVALS_MS[repeatSpeed.coerceIn(1, REPEAT_INTERVALS_MS.size) - 1]

    companion object {
        const val DEFAULT_REPEAT_SPEED = 3
        val REPEAT_INTERVALS_MS = listOf(150L, 115L, 90L, 65L, 45L)
    }
}
