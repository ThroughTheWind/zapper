package io.github.throughthewind.zapper.core.designsystem.components

import io.github.throughthewind.zapper.core.designsystem.theme.RemoteHaptic
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteHaptics
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Hold-to-repeat (docs/design/motion-and-haptics.md): send once, wait 400 ms, then every 90 ms,
 * and every 45 ms after 1.5 s, with a `RepeatTick` haptic per repeat. Runs until cancelled.
 */
internal suspend fun repeatWhileHeld(
    motion: RemoteMotion,
    haptics: RemoteHaptics,
    send: () -> Unit,
) {
    send()
    delay(motion.repeatInitialDelayMs)
    val start = System.currentTimeMillis()
    while (coroutineContext.isActive) {
        send()
        haptics.perform(RemoteHaptic.RepeatTick)
        val fast = System.currentTimeMillis() - start > motion.repeatAccelerateAfterMs
        delay(if (fast) motion.repeatFastIntervalMs else motion.repeatIntervalMs)
    }
}
