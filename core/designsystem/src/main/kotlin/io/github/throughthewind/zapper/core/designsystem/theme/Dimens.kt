package io.github.throughthewind.zapper.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val huge: Dp = 48.dp,
    val giant: Dp = 64.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

object Elevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
    val level4 = 8.dp
    val level5 = 12.dp
}

/** Remote canvas grid. Every key occupies whole cells; layouts are stored as (col, row, colSpan, rowSpan). */
object RemoteGrid {
    const val COLUMNS = 4
    const val COLUMNS_LARGE = 6 // width >= 600dp
    val margin = 16.dp
    val gutter = 12.dp
    val minCell = 64.dp
    val maxCell = 96.dp
    val minTouchTarget = 56.dp // larger than M3's 48dp: remotes are used eyes-on-TV

    fun columnsFor(widthDp: Dp) = if (widthDp >= 600.dp) COLUMNS_LARGE else COLUMNS

    fun cellSize(widthDp: Dp): Dp {
        val cols = columnsFor(widthDp)
        val raw = (widthDp - margin * 2 - gutter * (cols - 1)) / cols
        return raw.coerceIn(minCell, maxCell)
    }

    /** Pixel-free size of a key spanning [span] cells. */
    fun spanSize(
        cell: Dp,
        span: Int,
    ): Dp = cell * span + gutter * (span - 1)
}
