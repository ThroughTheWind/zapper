package io.github.throughthewind.zapper.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** M3 Expressive-style springs. Spatial = position/scale (may overshoot); Effects = color/alpha (no overshoot). */
@Immutable
class RemoteMotion {
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)

    val pressScale = 0.92f
    val editWiggleDegrees = 1.5f

    /** Hold-to-repeat (volume, channel, d-pad). */
    val repeatInitialDelayMs = 400L
    val repeatIntervalMs = 90L
    val repeatAccelerateAfterMs = 1500L
    val repeatFastIntervalMs = 45L
}

val LocalRemoteMotion = staticCompositionLocalOf { RemoteMotion() }
