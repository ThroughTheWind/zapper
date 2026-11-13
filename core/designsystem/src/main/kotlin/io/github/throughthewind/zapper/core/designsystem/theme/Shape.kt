package io.github.throughthewind.zapper.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val RemoteShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

object RemoteShapeTokens {
    val largeIncreased = RoundedCornerShape(20.dp)
    val extraLargeIncreased = RoundedCornerShape(32.dp)
    val extraExtraLarge = RoundedCornerShape(48.dp)
    val pill = CircleShape
}

/** Shape for a 1x1 remote key according to the user's KeyStyle. Multi-cell keys (rockers, 2x1) always use pill/rounded. */
val KeyStyle.shape: Shape
    get() =
        when (this) {
            KeyStyle.Rounded -> RoundedCornerShape(20.dp)

            KeyStyle.Squircle -> RoundedCornerShape(30)

            // replace with a superellipse shape (e.g. androidx.graphics.shapes) if desired
            KeyStyle.Circle -> CircleShape

            KeyStyle.Square -> RoundedCornerShape(8.dp)
        }

val keyShape: Shape
    @Composable @ReadOnlyComposable
    get() = LocalKeyStyle.current.shape
