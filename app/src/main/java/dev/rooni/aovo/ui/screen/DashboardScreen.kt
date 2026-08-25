package dev.rooni.aovo.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import dev.rooni.aovo.data.TileHeights
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.R
import dev.rooni.aovo.data.DashboardLayout
import dev.rooni.aovo.data.DashboardTile
import dev.rooni.aovo.data.TileType
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.Haptic
import dev.rooni.aovo.ui.LocalHaptics
import dev.rooni.aovo.ui.component.SectionDefaults
import dev.rooni.aovo.ui.component.actionTileShape
import kotlin.math.abs

@Composable
fun DashboardScreen(
    viewModel: AovoViewModel,
    onOpenDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection by viewModel.core.connection.collectAsStateWithLifecycle()
    val telemetry by viewModel.core.telemetry.collectAsStateWithLifecycle()
    val ride by viewModel.core.ride.collectAsStateWithLifecycle()
    val device by viewModel.core.connectedDevice.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editing by viewModel.editingDashboard.collectAsStateWithLifecycle()
    val gears by viewModel.core.gears.collectAsStateWithLifecycle()
    val sensors by viewModel.core.sensors.collectAsStateWithLifecycle()

    val context = TileContext(
        viewModel = viewModel,
        connection = connection,
        telemetry = telemetry,
        ride = ride,
        deviceName = device?.name.orEmpty(),
        gears = gears,
        sensors = sensors,
        onOpenDevices = onOpenDevices,
        editing = editing,
    )

    var showPicker by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = editing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            EditToolbar(
                onAdd = { showPicker = true },
                onReset = { confirmReset = true },
                onDone = { viewModel.setEditingDashboard(false) },
            )
        }

        DashboardGrid(
            layout = settings.dashboard,
            context = context,
            editing = editing,
            modifier = Modifier.weight(1f),
            onCommitOrder = viewModel::setTileOrder,
            onResize = viewModel::resizeTile,
            onSetHeight = viewModel::setTileHeight,
            onRemove = viewModel::removeTile,
            onStartEditing = { viewModel.setEditingDashboard(true) },
        )
    }

    if (showPicker) {
        TilePickerSheet(
            viewModel = viewModel,
            onDismiss = { showPicker = false },
            onPick = { type, profileId ->
                viewModel.addTile(type, profileId)
                showPicker = false
            },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_dashboard),
            message = stringResource(R.string.reset_dashboard_confirm),
            onDismiss = { confirmReset = false },
            onConfirm = {
                viewModel.resetDashboard()
                confirmReset = false
            },
        )
    }
}

@Composable
private fun DashboardGrid(
    layout: DashboardLayout,
    context: TileContext,
    editing: Boolean,
    modifier: Modifier,
    onCommitOrder: (List<DashboardTile>) -> Unit,
    onResize: (String, Int) -> Unit,
    onSetHeight: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onStartEditing: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val haptics = LocalHaptics.current

    // this the dashboard reopens wherever it was left instead of at the speedometer.
    LaunchedEffect(Unit) { gridState.scrollToItem(0) }

    // through storage would feed a stale order back into the drag and make the tile jump,
    // so the drag edits this list and only the final order is saved.
    var working by remember(layout) { mutableStateOf(layout.tiles) }
    val tiles = working

    val reorder = rememberGridReorderState(
        gridState = gridState,
        onMove = { from, to ->
            if (from in tiles.indices && to in tiles.indices) {
                haptics?.perform(Haptic.Tick)
                working = tiles.toMutableList().apply { add(to, removeAt(from)) }
            }
        },
        onCommit = {
            haptics?.perform(Haptic.Confirm)
            onCommitOrder(working)
        },
    )

    // Every edit lands on the working list first so the next gesture reads the new value
    // straight away, and is persisted in the same breath.
    fun edit(transform: (DashboardTile) -> DashboardTile, id: String) {
        working = working.map { if (it.id == id) transform(it) else it }
    }

    val resizeTile: (String, Int) -> Unit = { id, span ->
        edit({ it.copy(span = it.copy(span = span).clampedSpan()) }, id)
        onResize(id, span)
    }
    val setTileHeight: (String, Int) -> Unit = { id, step ->
        edit(
            {
                if (it.type.canResizeHeight) {
                    it.copy(
                        heightStep = step.coerceIn(TileHeights.minFor(it.type), TileHeights.MAX)
                    )
                } else {
                    it
                }
            },
            id,
        )
        onSetHeight(id, step)
    }
    val removeTile: (String) -> Unit = { id ->
        working = working.filterNot { it.id == id }
        onRemove(id)
    }
    val tileShape = RoundedCornerShape(20.dp)

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(DashboardLayout.COLUMNS),
        modifier = modifier
            .fillMaxSize()
            .reorderable(reorder, editing),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 10.dp,
            bottom = 40.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        verticalArrangement = Arrangement.spacedBy(TILE_GAP),
    ) {
        itemsIndexed(
            items = tiles,
            key = { _, tile -> tile.id },
            span = { _, tile -> GridItemSpan(tile.clampedSpan()) },
        ) { _, tile ->
            val dragging = reorder.draggingKey == tile.id
            val translation = reorder.offsetFor(tile.id)
            val lift by animateFloatAsState(
                targetValue = if (dragging) 1.04f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "lift",
            )
            val elevation by animateDpAsState(
                targetValue = if (dragging) 18.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "elevation",
            )

            var tileSize by remember(tile.id) { mutableStateOf(IntSize.Zero) }

            Box(
                modifier = Modifier
                    .onSizeChanged { tileSize = it }
                    .then(
                        if (dragging) {
                            Modifier.zIndex(2f)
                        } else {
                            // Springy placement so tiles glide aside instead of snapping.
                            Modifier.animateItem(
                                placementSpec = spring(
                                    dampingRatio = 0.78f,
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                ),
                            )
                        }
                    )
                    .graphicsLayer {
                        translationX = translation.x
                        translationY = translation.y
                        scaleX = lift
                        scaleY = lift
                        shadowElevation = elevation.toPx()
                        shape = tileShape
                        clip = false
                    }
                    // A width change should grow into place rather than jump.
                    .animateContentSize(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    )
                    .then(if (editing) Modifier.heightIn(min = 80.dp) else Modifier)
            ) {
                EditableTile(
                    tile = tile,
                    shape = tileShape,
                    editing = editing,
                    context = context,
                    tileSize = tileSize,
                    columnGap = TILE_GAP,
                    onResize = resizeTile,
                    onSetHeight = setTileHeight,
                    onRemove = removeTile,
                )
            }
        }

        if (!editing) {
            item(span = { GridItemSpan(DashboardLayout.COLUMNS) }) {
                TextButton(
                    onClick = onStartEditing,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp))
                    Text("  " + stringResource(R.string.edit_dashboard))
                }
            }
        }
    }
}

@Composable
private fun EditableTile(
    tile: DashboardTile,
    shape: RoundedCornerShape,
    editing: Boolean,
    context: TileContext,
    tileSize: IntSize,
    columnGap: Dp,
    onResize: (String, Int) -> Unit,
    onSetHeight: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    // Action tiles carry their state in their corners, so the editing outline has to follow
    // the same shape or it would sit proud of the tile it is outlining.
    val outlineShape = if (tile.type.isActionTile()) {
        actionTileShape(tileIsActive(tile, context))
    } else {
        shape
    }

    Box {
        DashboardTileContent(tile, shape, context)

        if (editing) {
            if (tile.type == TileType.Spacer) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                            outlineShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tile_spacer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, MaterialTheme.colorScheme.primary, outlineShape)
            )

            RemoveChip(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                onClick = { onRemove(tile.id) },
            )

            if (tile.type.canResizeWidth) {
                ResizeHandleBar(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                    tile = tile,
                    tileSize = tileSize,
                    columnGap = columnGap,
                    onResize = onResize,
                    onSetHeight = onSetHeight,
                )
            }
            if (tile.type.canResizeHeight) {
                ResizeHandleBar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                    tile = tile,
                    tileSize = tileSize,
                    columnGap = columnGap,
                    onResize = { _, _ -> },
                    onSetHeight = onSetHeight,
                )
            }
        }
    }
}

@Composable
private fun RemoveChip(modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 3.dp,
        modifier = modifier.size(28.dp),
    ) {
        Box(
            modifier = Modifier.clickable {
                haptics?.perform(Haptic.Reject)
                onClick()
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.remove_tile),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ResizeHandleBar(
    modifier: Modifier,
    tile: DashboardTile,
    tileSize: IntSize,
    columnGap: Dp,
    onResize: (String, Int) -> Unit,
    onSetHeight: (String, Int) -> Unit,
) {
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val gapPx = with(density) { columnGap.toPx() }
    val heightStepPx = with(density) { GAUGE_HEIGHT_STEP.toPx() }

    val current by rememberUpdatedState(tile)
    val currentSize by rememberUpdatedState(tileSize)

    val horizontal = tile.type.canResizeWidth
    val vertical = tile.type.canResizeHeight

    var startSpan by remember(tile.id) { mutableIntStateOf(tile.clampedSpan()) }
    var startHeight by remember(tile.id) { mutableIntStateOf(tile.clampedHeight()) }
    var startColumnPx by remember(tile.id) { mutableFloatStateOf(0f) }
    var total by remember(tile.id) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(tile.id, horizontal, vertical) {
                detectDragGestures(
                    onDragStart = {
                        startSpan = current.clampedSpan()
                        startHeight = current.clampedHeight()
                        total = Offset.Zero
                        // Width of one column, worked back out of the tile we are attached to.
                        val width = currentSize.width.toFloat()
                        startColumnPx = if (startSpan > 0 && width > 0f) {
                            (width - gapPx * (startSpan - 1)) / startSpan
                        } else {
                            0f
                        }
                    },
                    onDragEnd = { total = Offset.Zero },
                    onDragCancel = { total = Offset.Zero },
                ) { change, delta ->
                    change.consume()
                    total += delta

                    if (horizontal && startColumnPx > 0f) {
                        val stride = startColumnPx + gapPx
                        val wanted = startSpan + Math.round(total.x / stride)
                        val snapped = current.copy(
                            span = wanted.coerceIn(1, DashboardLayout.COLUMNS)
                        ).clampedSpan()
                        if (snapped != current.clampedSpan()) {
                            onResize(current.id, snapped)
                            haptics?.perform(Haptic.Tick)
                        }
                    }

                    if (vertical) {
                        val wanted = (startHeight + Math.round(total.y / heightStepPx))
                            .coerceIn(TileHeights.minFor(current.type), TileHeights.MAX)
                        if (wanted != current.clampedHeight()) {
                            onSetHeight(current.id, wanted)
                            haptics?.perform(Haptic.Tick)
                        }
                    }
                }
            }
            .padding(
                if (vertical && !horizontal) PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                else PaddingValues(horizontal = 6.dp, vertical = 10.dp)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (vertical && !horizontal) 54.dp else 6.dp,
                    height = if (vertical && !horizontal) 6.dp else 36.dp,
                )
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(50),
                ),
        )
    }
}

@Composable
private fun EditToolbar(onAdd: () -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.edit_dashboard),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                TextButton(onClick = onReset) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        stringResource(R.string.reset_dashboard),
                        modifier = Modifier.size(18.dp),
                    )
                }
                TextButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
                    Text(" " + stringResource(R.string.add_tile))
                }
                TextButton(onClick = onDone) { Text(stringResource(R.string.done)) }
            }
            Text(
                text = stringResource(R.string.edit_dashboard_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp, end = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TilePickerSheet(
    viewModel: AovoViewModel,
    onDismiss: () -> Unit,
    onPick: (TileType, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val plainTypes = TileType.entries.filter { it != TileType.Profile }
    val profileWord = stringResource(R.string.profile)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(R.string.add_tile),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 460.dp)
                .padding(bottom = 28.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(plainTypes, key = { it.key }) { type ->
                PickerRow(tileLabel(type, null)) { onPick(type, null) }
            }
            items(settings.profiles, key = { it.id }) { profile ->
                PickerRow(profileWord + " · " + profile.name) {
                    onPick(TileType.Profile, profile.id)
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

/** Gap between tiles, and the distance one height step covers. */
private val TILE_GAP = 10.dp
private val GAUGE_HEIGHT_STEP = 80.dp
