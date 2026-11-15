package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.throughthewind.zapper.core.designsystem.theme.LocalPowerColors
import io.github.throughthewind.zapper.core.designsystem.theme.LocalRemoteMotion
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteGrid
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteHaptic
import io.github.throughthewind.zapper.core.designsystem.theme.keyShape
import io.github.throughthewind.zapper.core.designsystem.theme.rememberRemoteHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Visual emphasis of a key. Users can override per key in edit mode (except Power keeps its own family by default). */
enum class KeyEmphasis { Standard, Primary, Secondary, Tertiary, Power, Ghost }

data class KeyColors(
    val container: Color,
    val content: Color,
)

@Composable
fun KeyEmphasis.colors(): KeyColors {
    val c = MaterialTheme.colorScheme
    val p = LocalPowerColors.current
    return when (this) {
        KeyEmphasis.Standard -> KeyColors(c.surfaceContainerHigh, c.onSurface)
        KeyEmphasis.Primary -> KeyColors(c.primary, c.onPrimary)
        KeyEmphasis.Secondary -> KeyColors(c.secondaryContainer, c.onSecondaryContainer)
        KeyEmphasis.Tertiary -> KeyColors(c.tertiaryContainer, c.onTertiaryContainer)
        KeyEmphasis.Power -> KeyColors(p.powerContainer, p.onPowerContainer)
        KeyEmphasis.Ghost -> KeyColors(Color.Transparent, c.onSurfaceVariant)
    }
}

/**
 * Base remote key. Every specialised key (number, app shortcut, media) is a RemoteKey with different content.
 *
 * States: enabled / pressed (scale 0.92 + 10% state layer) / disabled (TV not connected: 38% content, 12% container)
 *         / repeating (hold-to-repeat, ticks haptic each send) / editing (see EditableKey).
 */
@Composable
fun RemoteKey(
    label: String, // accessibility label, e.g. "Volume up"
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: KeyEmphasis = KeyEmphasis.Standard,
    shape: Shape = keyShape,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val motion = LocalRemoteMotion.current
    val haptics = rememberRemoteHaptics()
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val disabledPress = LocalDisabledKeyPress.current

    val base = emphasis.colors()
    val stateLayer = if (pressed) base.content.copy(alpha = 0.10f) else Color.Transparent
    val container by animateColorAsState(
        if (enabled) base.container else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        motion.defaultEffects(),
        label = "container",
    )
    val contentColor = if (enabled) base.content else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val scale by animateFloatAsState(if (pressed) motion.pressScale else 1f, motion.fastSpatial(), label = "scale")

    Box(
        modifier
            .defaultMinSize(RemoteGrid.minTouchTarget, RemoteGrid.minTouchTarget)
            .scale(scale)
            .clip(shape)
            .background(container)
            .background(stateLayer)
            .then(if (emphasis == KeyEmphasis.Ghost) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier)
            .keySemantics(label, enabled, onPress, onLongPress)
            .pointerInput(enabled, repeatOnHold, disabledPress) {
                if (!enabled) {
                    // A disabled key still says why nothing happens (RemoteKey: Disabled).
                    disabledPress?.let { tell -> detectTapGestures(onTap = { tell() }) }
                    return@pointerInput
                }
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.perform(RemoteHaptic.KeyPress)
                        val press = PressInteraction.Press(it)
                        interaction.emit(press)
                        val repeater =
                            if (repeatOnHold) {
                                scope.launch {
                                    onPress()
                                    delay(motion.repeatInitialDelayMs)
                                    val start = System.currentTimeMillis()
                                    while (isActive) {
                                        onPress()
                                        haptics.perform(RemoteHaptic.RepeatTick)
                                        val fast = System.currentTimeMillis() - start > motion.repeatAccelerateAfterMs
                                        delay(if (fast) motion.repeatFastIntervalMs else motion.repeatIntervalMs)
                                    }
                                }
                            } else {
                                null
                            }
                        val released = tryAwaitRelease()
                        repeater?.cancel()
                        pressed = false
                        haptics.perform(RemoteHaptic.KeyRelease)
                        interaction.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        if (released && !repeatOnHold) onPress()
                    },
                    onLongPress =
                        onLongPress?.let { cb ->
                            {
                                haptics.perform(RemoteHaptic.LongPress)
                                cb()
                            }
                        },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/**
 * What a disabled key does when tapped, provided by the screen that knows why it is disabled: the
 * remote shows "Not connected" with Reconnect. Null, as in edit mode, keeps disabled keys inert.
 */
val LocalDisabledKeyPress = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * One TalkBack button per key (SPEC NFR-9): the label names the action, a double tap presses once
 * even on keys that repeat while held, and a disabled key is announced as disabled. The key's own
 * icon or text is not read twice.
 */
private fun Modifier.keySemantics(
    label: String,
    enabled: Boolean,
    onPress: () -> Unit,
    onLongPress: (() -> Unit)?,
): Modifier =
    clearAndSetSemantics {
        contentDescription = label
        role = Role.Button
        if (enabled) {
            onClick {
                onPress()
                true
            }
            onLongPress?.let { action ->
                onLongClick {
                    action()
                    true
                }
            }
        } else {
            disabled()
        }
    }

/** Smallest size a key's text shrinks to when the user's font size does not fit the key. */
private val MinKeyText = 8.sp

/**
 * Text inside a key: follows the user's font size (SPEC NFR-9), and shrinks to fit when that is
 * larger than the key, instead of being cut off.
 */
@Composable
fun KeyText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val ink = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = ink, textAlign = TextAlign.Center),
        softWrap = false,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MinKeyText, maxFontSize = style.fontSize, stepSize = 1.sp),
    )
}
