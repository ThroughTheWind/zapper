package io.github.throughthewind.zapper.core.designsystem.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

enum class HapticsLevel { Off, Light, Strong }

val LocalHaptics = staticCompositionLocalOf { HapticsLevel.Light }

/** Semantic haptic events. Components call these, never raw constants. */
enum class RemoteHaptic { KeyPress, KeyRelease, RepeatTick, DpadSwipeStep, LongPress, DragStart, DragSnap, DragDrop, Success, Failure }

class RemoteHaptics(
    private val view: View,
    private val level: HapticsLevel,
) {
    fun perform(event: RemoteHaptic) {
        if (level == HapticsLevel.Off) return
        val sdk = Build.VERSION.SDK_INT
        val constant =
            when (event) {
                RemoteHaptic.KeyPress -> {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }

                RemoteHaptic.KeyRelease -> {
                    if (sdk >= 27) HapticFeedbackConstants.VIRTUAL_KEY_RELEASE else return
                }

                RemoteHaptic.RepeatTick -> {
                    HapticFeedbackConstants.CLOCK_TICK
                }

                RemoteHaptic.DpadSwipeStep -> {
                    if (sdk >=
                        34
                    ) {
                        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }
                }

                RemoteHaptic.LongPress -> {
                    HapticFeedbackConstants.LONG_PRESS
                }

                RemoteHaptic.DragStart -> {
                    if (sdk >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.LONG_PRESS
                }

                RemoteHaptic.DragSnap -> {
                    if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
                }

                RemoteHaptic.DragDrop -> {
                    if (sdk >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.VIRTUAL_KEY
                }

                RemoteHaptic.Success -> {
                    if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                }

                RemoteHaptic.Failure -> {
                    if (sdk >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                }
            }
        // Light: system feedback (respects the user's system touch-feedback setting).
        // Strong: TODO use Vibrator.areAllPrimitivesSupported + VibrationEffect.Composition (API 31+) for crisper clicks.
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberRemoteHaptics(): RemoteHaptics {
    val view = LocalView.current
    val level = LocalHaptics.current
    return remember(view, level) { RemoteHaptics(view, level) }
}
