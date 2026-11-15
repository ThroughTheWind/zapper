package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The colour roles an app shortcut may take; tertiary by default (docs/design/components/AppShortcut). */
enum class AppShortcutColor(
    val emphasis: KeyEmphasis,
) {
    Primary(KeyEmphasis.Primary),
    Secondary(KeyEmphasis.Secondary),
    Tertiary(KeyEmphasis.Tertiary),
}

/**
 * A tile that opens an app on the TV in one tap (docs/design/components/AppShortcut). 2x1 shows a
 * monogram well and the name; 1x1 the well alone, with the name as its description; 2x2 a large
 * well with the name below. Zapper ships no brand logos, so the well holds the first letter.
 */
@Composable
fun AppShortcut(
    name: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    rows: Int = 1,
    color: AppShortcutColor = AppShortcutColor.Tertiary,
    enabled: Boolean = true,
) {
    val container = color.emphasis.colors().container
    RemoteKey(
        label = "Open $name",
        onPress = onPress,
        emphasis = color.emphasis,
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        modifier = modifier,
    ) {
        when {
            columns >= 2 && rows >= 2 -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Monogram(name, ink = container, size = 56.dp)
                    AppName(name)
                }
            }

            columns >= 2 -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Monogram(name, ink = container, size = 40.dp)
                    AppName(name, Modifier.weight(1f))
                }
            }

            else -> {
                Monogram(name, ink = container, size = 40.dp)
            }
        }
    }
}

/** The first letter on a well in the key's content colour, inked in its container colour. */
@Composable
private fun Monogram(
    name: String,
    ink: Color,
    size: Dp,
) {
    Box(
        Modifier
            .size(size)
            .background(LocalContentColor.current, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            color = ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AppName(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}
