package io.github.throughthewind.zapper.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * WCAG AA contrast in every bundled theme (SPEC NFR-9): 4.5:1 for text and icons on the surfaces
 * the remote draws them on, 3:1 for outlines that mark a key's edge. Dynamic colour comes from the
 * phone and cannot be checked here.
 */
class ContrastTest {
    private val text: List<Pair<String, (ColorScheme) -> Pair<Color, Color>>> =
        listOf(
            "onSurface on surface" to { it.onSurface to it.surface },
            "onSurface on a Standard key" to { it.onSurface to it.surfaceContainerHigh },
            "onSurfaceVariant on surface" to { it.onSurfaceVariant to it.surface },
            "onSurfaceVariant on a Standard key" to { it.onSurfaceVariant to it.surfaceContainerHigh },
            "onSurfaceVariant on the rocker" to { it.onSurfaceVariant to it.surfaceContainerHighest },
            "onPrimary on a Primary key" to { it.onPrimary to it.primary },
            "onPrimaryContainer on primaryContainer" to { it.onPrimaryContainer to it.primaryContainer },
            "onSecondaryContainer on a Secondary key" to { it.onSecondaryContainer to it.secondaryContainer },
            "onTertiaryContainer on a Tertiary key" to { it.onTertiaryContainer to it.tertiaryContainer },
            "onErrorContainer on the offline banner" to { it.onErrorContainer to it.errorContainer },
            "primary text on surface" to { it.primary to it.surface },
            "error text on surface" to { it.error to it.surface },
        )

    private val edges: List<Pair<String, (ColorScheme) -> Pair<Color, Color>>> =
        listOf(
            "Ghost key outline on surface" to { it.outline to it.surface },
        )

    @Test
    fun `every bundled theme meets WCAG AA`() {
        val failures =
            SeedColor.entries.flatMap { seed ->
                ThemeMode.entries.flatMap { mode ->
                    val scheme = schemeFor(seed, mode)
                    (failing(text, scheme, 4.5) + failing(edges, scheme, 3.0)).map { "$seed $mode, $it" }
                }
            }
        assertTrue(failures.isEmpty(), "Below WCAG AA:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `power keys meet WCAG AA in light and dark`() {
        val failures =
            listOf("light" to PowerColorsLight, "dark" to PowerColorsDark).flatMap { (name, p) ->
                listOfNotNull(
                    below("$name onPower on power", p.onPower, p.power, 4.5),
                    below("$name onPowerContainer on powerContainer", p.onPowerContainer, p.powerContainer, 4.5),
                )
            }
        assertTrue(failures.isEmpty(), "Below WCAG AA:\n" + failures.joinToString("\n"))
    }

    private fun failing(
        pairs: List<Pair<String, (ColorScheme) -> Pair<Color, Color>>>,
        scheme: ColorScheme,
        minimum: Double,
    ): List<String> =
        pairs.mapNotNull { (name, pick) ->
            val (fg, bg) = pick(scheme)
            below(name, fg, bg, minimum)
        }

    private fun below(
        name: String,
        fg: Color,
        bg: Color,
        minimum: Double,
    ): String? {
        val ratio = contrast(fg, bg)
        return if (ratio < minimum) "$name: ${"%.2f".format(ratio)} < $minimum" else null
    }

    /** WCAG 2.x contrast ratio, computed from the sRGB channels. */
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
