package dev.rooni.aovo.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SectionDefaults {
    /** Radius on an edge that faces the screen. */
    val OuterCorner: Dp = 24.dp

    /** Radius on an edge that touches a sibling tile. */
    val InnerCorner: Dp = 6.dp

    /** Hairline gap that keeps the seam between tiles visible. */
    val Gap: Dp = 3.dp

    val HorizontalPadding: Dp = 12.dp

    /** Corner set for one tile in a single-column run. */
    fun columnShape(index: Int, count: Int): RoundedCornerShape {
        val top = if (index == 0) OuterCorner else InnerCorner
        val bottom = if (index == count - 1) OuterCorner else InnerCorner
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }

    /** Corner set for one tile in a grid; only the four block corners stay round. */
    fun gridShape(row: Int, column: Int, rows: Int, columns: Int): RoundedCornerShape =
        RoundedCornerShape(
            topStart = if (row == 0 && column == 0) OuterCorner else InnerCorner,
            topEnd = if (row == 0 && column == columns - 1) OuterCorner else InnerCorner,
            bottomStart = if (row == rows - 1 && column == 0) OuterCorner else InnerCorner,
            bottomEnd = if (row == rows - 1 && column == columns - 1) OuterCorner else InnerCorner,
        )
}

/** Collects the tiles of a section so their shapes and animations can be derived from their position. */
class SectionScope internal constructor() {
    internal data class TileEntry(
        val visible: Boolean = true,
        val content: @Composable (RoundedCornerShape) -> Unit,
    )

    internal val tiles = mutableListOf<TileEntry>()

    /** Adds one tile. It receives the shape it should draw itself with. */
    fun tile(content: @Composable (RoundedCornerShape) -> Unit) {
        tiles.add(TileEntry(true, content))
    }

    /** Adds a tile only when [condition] holds, animated smoothly with expressive motion. */
    fun tileIf(condition: Boolean, content: @Composable (RoundedCornerShape) -> Unit) {
        tiles.add(TileEntry(condition, content))
    }
}

/** A single-column section: one rounded block made of stacked tiles with fluid M3E transitions. */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    content: SectionScope.() -> Unit,
) {
    val scope = SectionScope().apply(content)
    if (scope.tiles.isEmpty()) return

    val visibleIndices = scope.tiles.indices.filter { scope.tiles[it].visible }
    val firstVisible = visibleIndices.firstOrNull() ?: -1
    val lastVisible = visibleIndices.lastOrNull() ?: -1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SectionDefaults.HorizontalPadding),
    ) {
        scope.tiles.forEachIndexed { index, entry ->
            val isFirst = index == firstVisible
            val isLast = index == lastVisible

            val topCorner by animateDpAsState(
                targetValue = if (isFirst) SectionDefaults.OuterCorner else SectionDefaults.InnerCorner,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "topCorner_$index"
            )
            val bottomCorner by animateDpAsState(
                targetValue = if (isLast) SectionDefaults.OuterCorner else SectionDefaults.InnerCorner,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "bottomCorner_$index"
            )

            val animatedShape = RoundedCornerShape(
                topStart = topCorner,
                topEnd = topCorner,
                bottomStart = bottomCorner,
                bottomEnd = bottomCorner,
            )

            AnimatedVisibility(
                visible = entry.visible,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    expandFrom = Alignment.Top,
                ) + fadeIn(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                    )
                ),
            ) {
                Box(
                    modifier = Modifier.padding(
                        bottom = if (index != scope.tiles.lastIndex) SectionDefaults.Gap else 0.dp
                    )
                ) {
                    entry.content(animatedShape)
                }
            }
        }
    }
}

/** A section laid out as a grid, used for the metric and quick-action tiles. */
@Composable
fun SectionGrid(
    columns: Int = 2,
    modifier: Modifier = Modifier,
    content: SectionScope.() -> Unit,
) {
    val scope = SectionScope().apply(content)
    if (scope.tiles.isEmpty()) return
    val rows = (scope.tiles.size + columns - 1) / columns
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SectionDefaults.HorizontalPadding),
    ) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(SectionDefaults.Gap)) {
                for (column in 0 until columns) {
                    val index = row * columns + column
                    if (index < scope.tiles.size) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (row < rows - 1) SectionDefaults.Gap else 0.dp)
                        ) {
                            scope.tiles[index].content(
                                SectionDefaults.gridShape(row, column, rows, columns)
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
