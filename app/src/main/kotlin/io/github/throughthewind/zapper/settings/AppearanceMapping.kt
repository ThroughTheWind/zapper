package io.github.throughthewind.zapper.settings

import io.github.throughthewind.zapper.core.designsystem.theme.AppearancePrefs
import io.github.throughthewind.zapper.core.designsystem.theme.DarkModePref
import io.github.throughthewind.zapper.core.designsystem.theme.HapticsLevel
import io.github.throughthewind.zapper.core.designsystem.theme.KeyStyle
import io.github.throughthewind.zapper.core.designsystem.theme.SeedColor
import io.github.throughthewind.zapper.core.model.AppearanceSettings
import io.github.throughthewind.zapper.core.model.ColorChoice
import io.github.throughthewind.zapper.core.model.HapticStrength
import io.github.throughthewind.zapper.core.model.KeyShapeChoice
import io.github.throughthewind.zapper.core.model.ThemeChoice

/** The stored settings as the design system's theme takes them. */
fun AppearanceSettings.toPrefs(): AppearancePrefs =
    AppearancePrefs(
        darkMode =
            when (theme) {
                ThemeChoice.SYSTEM -> DarkModePref.System
                ThemeChoice.LIGHT -> DarkModePref.Light
                ThemeChoice.DARK -> DarkModePref.Dark
            },
        seed = color.seed(),
        keyStyle = keyShape.style(),
        haptics =
            when (haptics) {
                HapticStrength.OFF -> HapticsLevel.Off
                HapticStrength.LIGHT -> HapticsLevel.Light
                HapticStrength.STRONG -> HapticsLevel.Strong
            },
        oledBlack = oledBlack,
        clickSound = clickSound,
        repeatIntervalMs = repeatIntervalMs,
    )

/** The preset seed, or null for dynamic colour. */
internal fun ColorChoice.seed(): SeedColor? =
    when (this) {
        ColorChoice.DYNAMIC -> null
        ColorChoice.INDIGO -> SeedColor.Indigo
        ColorChoice.TEAL -> SeedColor.Teal
        ColorChoice.AMBER -> SeedColor.Amber
        ColorChoice.ROSE -> SeedColor.Rose
        ColorChoice.GREEN -> SeedColor.Green
        ColorChoice.GRAPHITE -> SeedColor.Graphite
    }

internal fun KeyShapeChoice.style(): KeyStyle =
    when (this) {
        KeyShapeChoice.ROUNDED -> KeyStyle.Rounded
        KeyShapeChoice.SQUIRCLE -> KeyStyle.Squircle
        KeyShapeChoice.CIRCLE -> KeyStyle.Circle
        KeyShapeChoice.SQUARE -> KeyStyle.Square
    }

internal val ThemeChoice.title: String
    get() =
        when (this) {
            ThemeChoice.SYSTEM -> "System"
            ThemeChoice.LIGHT -> "Light"
            ThemeChoice.DARK -> "Dark"
        }

internal val ColorChoice.title: String
    get() = if (this == ColorChoice.DYNAMIC) "Dynamic" else name.lowercase().replaceFirstChar { it.uppercase() }

internal val KeyShapeChoice.title: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

internal val HapticStrength.title: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }
