package io.github.throughthewind.zapper.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import io.github.throughthewind.zapper.R
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

@Composable
@ReadOnlyComposable
internal fun ThemeChoice.title(): String =
    stringResource(
        when (this) {
            ThemeChoice.SYSTEM -> R.string.theme_system
            ThemeChoice.LIGHT -> R.string.theme_light
            ThemeChoice.DARK -> R.string.theme_dark
        },
    )

@Composable
@ReadOnlyComposable
internal fun ColorChoice.title(): String =
    stringResource(
        when (this) {
            ColorChoice.DYNAMIC -> R.string.color_dynamic
            ColorChoice.INDIGO -> R.string.color_indigo
            ColorChoice.TEAL -> R.string.color_teal
            ColorChoice.AMBER -> R.string.color_amber
            ColorChoice.ROSE -> R.string.color_rose
            ColorChoice.GREEN -> R.string.color_green
            ColorChoice.GRAPHITE -> R.string.color_graphite
        },
    )

@Composable
@ReadOnlyComposable
internal fun KeyShapeChoice.title(): String =
    stringResource(
        when (this) {
            KeyShapeChoice.ROUNDED -> R.string.shape_rounded
            KeyShapeChoice.SQUIRCLE -> R.string.shape_squircle
            KeyShapeChoice.CIRCLE -> R.string.shape_circle
            KeyShapeChoice.SQUARE -> R.string.shape_square
        },
    )

@Composable
@ReadOnlyComposable
internal fun HapticStrength.title(): String =
    stringResource(
        when (this) {
            HapticStrength.OFF -> R.string.haptics_off
            HapticStrength.LIGHT -> R.string.haptics_light
            HapticStrength.STRONG -> R.string.haptics_strong
        },
    )
