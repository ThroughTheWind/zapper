package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.designsystem.theme.KeyType

/**
 * A generic TV silhouette showing what to look for on screen: an approval prompt with Allow
 * highlighted (docs/design/components/PairingProgress). The drawing copies no brand's UI.
 */
@Composable
fun PairingHero(
    prompt: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = colors.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier =
                Modifier
                    .width(240.dp)
                    .aspectRatio(16f / 9f)
                    .border(6.dp, colors.surfaceContainerHigh, MaterialTheme.shapes.medium)
                    .semantics { contentDescription = "Your TV shows: $prompt" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(color = colors.surface, shape = MaterialTheme.shapes.small, shadowElevation = 2.dp) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(prompt, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Surface(color = colors.surfaceContainerHigh, shape = CircleShape) {
                                Text(
                                    "Deny",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                )
                            }
                            Surface(color = colors.primary, contentColor = colors.onPrimary, shape = CircleShape) {
                                Text(
                                    "Allow",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        // Stand.
        Surface(color = colors.surfaceContainerHigh, modifier = Modifier.width(64.dp).height(6.dp)) {}
    }
}

/** One row of the pairing steps. */
enum class PairingStepState { Done, Current, Upcoming }

/** The three pairing steps: done (primary disc + check), current (spinner), upcoming (number). */
@Composable
fun PairingSteps(
    steps: List<String>,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { index, label ->
            val state =
                when {
                    index < current -> PairingStepState.Done
                    index == current -> PairingStepState.Current
                    else -> PairingStepState.Upcoming
                }
            PairingStep(index + 1, label, state)
        }
    }
}

@Composable
private fun PairingStep(
    number: Int,
    label: String,
    state: PairingStepState,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val (container, content) =
            when (state) {
                PairingStepState.Done -> colors.primary to colors.onPrimary
                PairingStepState.Current -> colors.primaryContainer to colors.onPrimaryContainer
                PairingStepState.Upcoming -> colors.surfaceContainerHighest to colors.onSurfaceVariant
            }
        Surface(color = container, contentColor = content, shape = CircleShape, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                when (state) {
                    PairingStepState.Done -> Icon(Icons.Rounded.Check, contentDescription = "Done", modifier = Modifier.size(18.dp))
                    PairingStepState.Current -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = content)
                    PairingStepState.Upcoming -> Text("$number", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (state == PairingStepState.Current) FontWeight.SemiBold else null,
            color = if (state == PairingStepState.Upcoming) colors.onSurfaceVariant else colors.onSurface,
        )
    }
}

/**
 * The code the TV shows: [length] boxes in the key-numeral style, the active box outlined 2dp in
 * primary. [onComplete] fires when the last character is typed.
 */
@Composable
fun PinEntry(
    value: String,
    length: Int,
    onValueChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    hex: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = { text ->
            val cleaned =
                text
                    .uppercase()
                    .filter { it.isDigit() || (hex && it in 'A'..'F') }
                    .take(length)
            onValueChange(cleaned)
            if (cleaned.length == length) onComplete(cleaned)
        },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = if (hex) KeyboardType.Ascii else KeyboardType.NumberPassword),
        modifier = modifier.semantics { contentDescription = "Code shown on the TV, $length characters" },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { index ->
                    val active = index == value.length && enabled
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(width = 48.dp, height = 60.dp)
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = if (active) colors.primary else colors.outline,
                                    shape = MaterialTheme.shapes.medium,
                                ),
                    ) {
                        Text(value.getOrNull(index)?.toString() ?: "", style = KeyType.numeral)
                    }
                }
            }
        },
    )
}

/** The outcome disc: success in primary-container, failure in error-container. */
@Composable
fun PairingOutcome(
    success: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (success) colors.primaryContainer else colors.errorContainer,
        contentColor = if (success) colors.onPrimaryContainer else colors.onErrorContainer,
        shape = CircleShape,
        modifier = modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (success) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = if (success) "Paired" else "Not paired",
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
