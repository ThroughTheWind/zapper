package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.designsystem.theme.KeyType
import io.github.throughthewind.zapper.core.designsystem.theme.LocalRemoteMotion
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteHaptic
import io.github.throughthewind.zapper.core.designsystem.theme.rememberRemoteHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

enum class DPadDirection { Up, Down, Left, Right, Ok }

/** Travel per step in swipe mode (docs/design/components/DPad). */
private val SwipeStep = 24.dp

/** OK's diameter as a share of the pad's. */
private const val OK_RATIO = 0.40f

/**
 * The circular directional pad (docs/design/components/DPad). Each quadrant is a 90° wedge hit
 * area; OK sits in the middle. Holding a wedge repeats; a drag that starts on the pad sends one
 * step per 24 dp along its dominant axis. TalkBack sees five buttons.
 */
@Composable
fun DPad(
    onPress: (DPadDirection) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val motion = LocalRemoteMotion.current
    val haptics = rememberRemoteHaptics()
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { SwipeStep.toPx() }
    var pressed by remember { mutableStateOf<DPadDirection?>(null) }
    val okScale by animateFloatAsState(if (pressed == DPadDirection.Ok) motion.pressScale else 1f, motion.fastSpatial(), label = "ok")
    val content = if (enabled) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.38f)

    Box(
        modifier
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val target = hit(down.position, center, size.width / 2f)
                    pressed = target
                    haptics.perform(RemoteHaptic.KeyPress)
                    var repeater: Job? =
                        if (target != DPadDirection.Ok) scope.launch { repeatWhileHeld(motion, haptics) { onPress(target) } } else null
                    var travel = Offset.Zero
                    var swiping = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        travel += change.positionChange()
                        if (!swiping && hypot(travel.x, travel.y) > viewConfiguration.touchSlop) {
                            swiping = true
                            repeater?.cancel()
                            repeater = null
                            pressed = null
                            travel = Offset.Zero
                        }
                        if (swiping) {
                            change.consume()
                            val horizontal = abs(travel.x) >= abs(travel.y)
                            val along = if (horizontal) travel.x else travel.y
                            if (abs(along) >= stepPx) {
                                val direction =
                                    when {
                                        horizontal && along > 0 -> DPadDirection.Right
                                        horizontal -> DPadDirection.Left
                                        along > 0 -> DPadDirection.Down
                                        else -> DPadDirection.Up
                                    }
                                onPress(direction)
                                haptics.perform(RemoteHaptic.DpadSwipeStep)
                                travel = Offset.Zero
                            }
                        }
                    }
                    repeater?.cancel()
                    if (!swiping) {
                        if (target == DPadDirection.Ok) onPress(DPadDirection.Ok)
                        haptics.perform(RemoteHaptic.KeyRelease)
                    }
                    pressed = null
                }
            },
    ) {
        val ring = colors.outlineVariant
        val body = if (enabled) colors.surfaceContainerHigh else colors.onSurface.copy(alpha = 0.12f)
        val pressedLayer = colors.onSurface.copy(alpha = 0.10f)
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(body, radius)
            drawCircle(ring, radius - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            val wedge = pressed
            if (wedge != null && wedge != DPadDirection.Ok) {
                val start =
                    when (wedge) {
                        DPadDirection.Right -> -45f
                        DPadDirection.Down -> 45f
                        DPadDirection.Left -> 135f
                        else -> 225f
                    }
                drawArc(pressedLayer, start, 90f, useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
            }
        }
        Chevron(Icons.Rounded.KeyboardArrowUp, "Up", Alignment.TopCenter, content, enabled) { onPress(DPadDirection.Up) }
        Chevron(Icons.Rounded.KeyboardArrowDown, "Down", Alignment.BottomCenter, content, enabled) { onPress(DPadDirection.Down) }
        Chevron(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            "Left",
            Alignment.CenterStart,
            content,
            enabled,
        ) { onPress(DPadDirection.Left) }
        Chevron(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            "Right",
            Alignment.CenterEnd,
            content,
            enabled,
        ) { onPress(DPadDirection.Right) }
        // OK: 40% of the diameter, an 8 dp ring of body colour around it.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(OK_RATIO)
                .aspectRatio(1f)
                .scale(okScale)
                .semantics {
                    contentDescription = "OK"
                    role = Role.Button
                    if (enabled) {
                        onClick {
                            onPress(DPadDirection.Ok)
                            true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(body, size.minDimension / 2f)
                drawCircle(if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.12f), size.minDimension / 2f - 8.dp.toPx())
            }
            Text("OK", style = KeyType.label, color = if (enabled) colors.onPrimary else content)
        }
    }
}

@Composable
private fun BoxScope.Chevron(
    icon: ImageVector,
    label: String,
    alignment: Alignment,
    tint: Color,
    enabled: Boolean,
    onPress: () -> Unit,
) {
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier =
            Modifier
                .align(alignment)
                .padding(18.dp)
                .size(28.dp)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    if (enabled) {
                        onClick {
                            onPress()
                            true
                        }
                    }
                },
    )
}

/** Which part of the pad a point at [position] falls in, for a pad of [radius] centred on [center]. */
private fun hit(
    position: Offset,
    center: Offset,
    radius: Float,
): DPadDirection {
    val dx = position.x - center.x
    val dy = position.y - center.y
    if (hypot(dx, dy) <= radius * OK_RATIO) return DPadDirection.Ok
    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
    return when {
        degrees >= -45 && degrees < 45 -> DPadDirection.Right
        degrees >= 45 && degrees < 135 -> DPadDirection.Down
        degrees >= -135 && degrees < -45 -> DPadDirection.Up
        else -> DPadDirection.Left
    }
}
