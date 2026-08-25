package dev.rooni.aovo.ui.screen

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

class GridReorderState(
    private val gridState: LazyGridState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onCommit: () -> Unit = {},
) {
    var draggingKey: Any? by mutableStateOf(null)
        private set

    private var initialItemOffset = Offset.Zero
    private var accumulatedDrag by mutableStateOf(Offset.Zero)
    private var currentDraggingIndex = -1

    /** Translation to apply to the dragged tile, in pixels. */
    fun offsetFor(key: Any): Offset {
        if (key != draggingKey) return Offset.Zero
        val currentItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            ?: return accumulatedDrag
        return (initialItemOffset + accumulatedDrag) - Offset(currentItem.offset.x.toFloat(), currentItem.offset.y.toFloat())
    }

    fun onDragStart(position: Offset) {
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contains(position) }
            ?: return
        draggingKey = item.key
        currentDraggingIndex = item.index
        initialItemOffset = Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
        accumulatedDrag = Offset.Zero
    }

    fun onDrag(delta: Offset) {
        if (draggingKey == null) return
        accumulatedDrag += delta

        val draggingItem = gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggingKey } ?: return

        val centre = initialItemOffset + accumulatedDrag + Offset(
            draggingItem.size.width / 2f,
            draggingItem.size.height / 2f,
        )

        // does not flip back and forth between two slots.
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != draggingKey && item.containsDeep(centre)
        } ?: return

        val fromIndex = draggingItem.index
        val toIndex = target.index
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0) return

        currentDraggingIndex = toIndex
        onMove(fromIndex, toIndex)
    }

    fun onDragEnd() {
        val wasDragging = draggingKey != null
        draggingKey = null
        currentDraggingIndex = -1
        accumulatedDrag = Offset.Zero
        initialItemOffset = Offset.Zero
        if (wasDragging) onCommit()
    }

    private fun LazyGridItemInfo.contains(point: Offset): Boolean =
        point.x >= offset.x && point.x <= offset.x + size.width &&
            point.y >= offset.y && point.y <= offset.y + size.height

    /** Containment with a generous inset, which is the hysteresis that stops oscillation. */
    private fun LazyGridItemInfo.containsDeep(point: Offset): Boolean {
        val marginX = size.width * 0.28f
        val marginY = size.height * 0.28f
        return point.x >= offset.x + marginX && point.x <= offset.x + size.width - marginX &&
            point.y >= offset.y + marginY && point.y <= offset.y + size.height - marginY
    }
}

@Composable
fun rememberGridReorderState(
    gridState: LazyGridState,
    onMove: (from: Int, to: Int) -> Unit,
    onCommit: () -> Unit = {},
): GridReorderState {
    // The state outlives individual recompositions, so the callbacks are read through
    // holders rather than captured once.
    val move by rememberUpdatedState(onMove)
    val commit by rememberUpdatedState(onCommit)
    return remember(gridState) {
        GridReorderState(gridState, { from, to -> move(from, to) }, { commit() })
    }
}

/** Attach to the grid itself so a long press anywhere picks up the tile beneath it. */
fun Modifier.reorderable(state: GridReorderState, enabled: Boolean): Modifier =
    if (!enabled) this else this.pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(it) },
            onDrag = { change, delta ->
                change.consume()
                state.onDrag(delta)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() },
        )
    }

