package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The scanning indicator (docs/design/components/DiscoveryScan): three primary rings pulse out of a
 * primary-container disc holding a TV icon, in a 2.4 s loop. Static when [animate] is false
 * (reduced motion).
 */
@Composable
fun DiscoveryScanIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    animate: Boolean = true,
) {
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "rings",
    )
    Box(modifier.size(size).semantics { contentDescription = "Looking for TVs" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxRadius = this.size.minDimension / 2
            val minRadius = maxRadius * 0.35f
            repeat(RINGS) { ring ->
                val t = if (animate) (progress + ring.toFloat() / RINGS) % 1f else (ring + 1f) / (RINGS + 1)
                drawCircle(
                    color = primary.copy(alpha = (1f - t) * 0.6f),
                    radius = minRadius + (maxRadius - minRadius) * t,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier =
                Modifier.size(size * 0.5f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

private const val LOOP_MS = 2_400
private const val RINGS = 3
