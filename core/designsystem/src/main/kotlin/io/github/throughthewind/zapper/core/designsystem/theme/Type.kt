package io.github.throughthewind.zapper.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Roboto Flex via androidx.compose.ui:ui-text-google-fonts (downloadable, OFL).
 * Swap to the GoogleFont-backed FontFamily once the stack thread confirms the dependency;
 * FontFamily.Default (Roboto on most devices) is a safe fallback.
 */
val BrandFont: FontFamily = FontFamily.Default

private fun style(
    size: Int,
    line: Int,
    weight: Int,
    tracking: Double,
) = TextStyle(
    fontFamily = BrandFont,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
)

val RemoteTypography =
    Typography(
        displayLarge = style(57, 64, 400, -0.25),
        displayMedium = style(45, 52, 400, 0.0),
        displaySmall = style(36, 44, 400, 0.0),
        headlineLarge = style(32, 40, 500, 0.0),
        headlineMedium = style(28, 36, 500, 0.0),
        headlineSmall = style(24, 32, 500, 0.0),
        titleLarge = style(22, 28, 500, 0.0),
        titleMedium = style(16, 24, 600, 0.15),
        titleSmall = style(14, 20, 600, 0.1),
        bodyLarge = style(16, 24, 400, 0.5),
        bodyMedium = style(14, 20, 400, 0.25),
        bodySmall = style(12, 16, 400, 0.4),
        labelLarge = style(14, 20, 600, 0.1),
        labelMedium = style(12, 16, 600, 0.5),
        labelSmall = style(11, 16, 600, 0.5),
    )

/** Remote-key specific styles. Numerals use tabular figures so the number pad aligns. */
object KeyType {
    val numeral = style(28, 32, 500, 0.0).copy(fontFeatureSettings = "tnum")
    val label = style(13, 16, 700, 0.8) // "HOME", "MENU" (uppercase)
    val labelSmall = style(11, 14, 700, 0.8) // rocker captions "VOL" / "CH"
}
