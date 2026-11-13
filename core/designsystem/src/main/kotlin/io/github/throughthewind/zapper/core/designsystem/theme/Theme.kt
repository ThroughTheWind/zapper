package io.github.throughthewind.zapper.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { Light, Dark, Oled }

enum class DarkModePref { System, Light, Dark, Oled }

enum class KeyStyle { Rounded, Squircle, Circle, Square }

/** Everything the user can change in Settings > Appearance. Persist this (DataStore). */
@Immutable
data class AppearancePrefs(
    val darkMode: DarkModePref = DarkModePref.System,
    /** null = dynamic color (Android 12+), falls back to Indigo below API 31. */
    val seed: SeedColor? = null,
    val keyStyle: KeyStyle = KeyStyle.Rounded,
    val haptics: HapticsLevel = HapticsLevel.Light,
)

@Immutable
data class PowerColors(
    val power: Color,
    val onPower: Color,
    val powerContainer: Color,
    val onPowerContainer: Color,
)

val LocalPowerColors = staticCompositionLocalOf { PowerColorsDark }
val LocalKeyStyle = staticCompositionLocalOf { KeyStyle.Rounded }

@Composable
fun ZapperTheme(
    prefs: AppearancePrefs = AppearancePrefs(),
    content: @Composable () -> Unit,
) {
    val mode =
        when (prefs.darkMode) {
            DarkModePref.System -> if (isSystemInDarkTheme()) ThemeMode.Dark else ThemeMode.Light
            DarkModePref.Light -> ThemeMode.Light
            DarkModePref.Dark -> ThemeMode.Dark
            DarkModePref.Oled -> ThemeMode.Oled
        }
    val context = LocalContext.current
    val scheme: ColorScheme =
        when {
            prefs.seed == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                when (mode) {
                    ThemeMode.Light -> dynamicLightColorScheme(context)
                    ThemeMode.Dark -> dynamicDarkColorScheme(context)
                    ThemeMode.Oled -> dynamicDarkColorScheme(context).toOled()
                }
            }

            else -> {
                schemeFor(prefs.seed ?: SeedColor.Indigo, mode)
            }
        }
    val power = if (mode == ThemeMode.Light) PowerColorsLight else PowerColorsDark

    CompositionLocalProvider(
        LocalPowerColors provides power,
        LocalKeyStyle provides prefs.keyStyle,
        LocalSpacing provides Spacing(),
        LocalRemoteMotion provides RemoteMotion(),
        LocalHaptics provides prefs.haptics,
    ) {
        MaterialTheme(colorScheme = scheme, typography = RemoteTypography, shapes = RemoteShapes, content = content)
    }
}

/** Pure-black base for OLED panels; keeps dynamic accents. */
private fun ColorScheme.toOled() =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = surfaceContainerLow.blendToBlack(0.6f),
        surfaceContainer = surfaceContainer.blendToBlack(0.5f),
        surfaceContainerHigh = surfaceContainerHigh.blendToBlack(0.4f),
        surfaceContainerHighest = surfaceContainerHighest.blendToBlack(0.3f),
    )

private fun Color.blendToBlack(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
