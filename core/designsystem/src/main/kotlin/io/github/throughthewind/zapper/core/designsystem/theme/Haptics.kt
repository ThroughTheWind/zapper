package io.github.throughthewind.zapper.core.designsystem.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

enum class HapticsLevel { Off, Light, Strong }

val LocalHaptics = staticCompositionLocalOf { HapticsLevel.Light }

/** Whether a key press also clicks (SPEC FR-10.2). */
val LocalClickSound = staticCompositionLocalOf { false }

/** Semantic haptic events. Components call these, never raw constants. */
enum class RemoteHaptic { KeyPress, KeyRelease, RepeatTick, DpadSwipeStep, LongPress, DragStart, DragSnap, DragDrop, Success, Failure }

class RemoteHaptics(
    private val view: View,
    private val level: HapticsLevel,
    private val clickSound: Boolean = false,
) {
    private val vibrator: Vibrator? by lazy { view.context.vibrator() }

    fun perform(event: RemoteHaptic) {
        if (clickSound && event == RemoteHaptic.KeyPress) view.playSoundEffect(SoundEffectConstants.CLICK)
        if (level == HapticsLevel.Off) return
        if (level == HapticsLevel.Strong && strong(event)) return
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
        view.performHapticFeedback(constant)
    }

    /**
     * Strong: the vibrator's predefined heavy clicks (API 29+), which most phones play crisper and
     * stronger than view feedback. False when not handled here, so the light feedback plays instead.
     */
    private fun strong(event: RemoteHaptic): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val target = vibrator?.takeIf { it.hasVibrator() } ?: return false
        val effect =
            when (event) {
                RemoteHaptic.KeyPress, RemoteHaptic.LongPress, RemoteHaptic.DragDrop -> VibrationEffect.EFFECT_HEAVY_CLICK
                RemoteHaptic.KeyRelease -> return true
                RemoteHaptic.RepeatTick, RemoteHaptic.DpadSwipeStep, RemoteHaptic.DragSnap -> VibrationEffect.EFFECT_CLICK
                RemoteHaptic.DragStart -> VibrationEffect.EFFECT_HEAVY_CLICK
                RemoteHaptic.Success, RemoteHaptic.Failure -> VibrationEffect.EFFECT_DOUBLE_CLICK
            }
        target.vibrate(VibrationEffect.createPredefined(effect))
        return true
    }
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }

@Composable
fun rememberRemoteHaptics(): RemoteHaptics {
    val view = LocalView.current
    val level = LocalHaptics.current
    val sound = LocalClickSound.current
    return remember(view, level, sound) { RemoteHaptics(view, level, sound) }
}
