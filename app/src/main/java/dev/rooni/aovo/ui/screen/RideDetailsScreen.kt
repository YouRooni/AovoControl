package dev.rooni.aovo.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rooni.aovo.R
import dev.rooni.aovo.ride.RideSample
import dev.rooni.aovo.ride.RideSession
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.component.MetricCard
import dev.rooni.aovo.ui.component.SectionGrid
import dev.rooni.aovo.ui.component.SectionTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ChartType {
    SPEED, POWER, CURRENT, VOLTAGE, BATTERY, TEMPERATURE
}

@Composable
fun RideDetailsScreen(
    viewModel: AovoViewModel,
    rideId: Long,
    modifier: Modifier = Modifier,
) {
    var ride by remember { mutableStateOf<RideSession?>(null) }
    var selectedChart by remember { mutableStateOf(ChartType.SPEED) }

    LaunchedEffect(rideId) {
        ride = viewModel.getRideDetails(rideId)
    }

    val currentRide = ride
    if (currentRide == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Summary Card
        RideSummaryCard(currentRide)

        // Metrics grid
        SectionGrid {
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.stat_avg_speed),
                    value = String.format(Locale.US, "%.1f", currentRide.avgSpeed),
                    unit = stringResource(R.string.unit_kmh),
                    icon = Icons.Filled.Speed,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.stat_top_speed),
                    value = String.format(Locale.US, "%.1f", currentRide.maxSpeed),
                    unit = stringResource(R.string.unit_kmh),
                    icon = Icons.Filled.Speed,
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.stat_top_power),
                    value = currentRide.maxPowerWatts.toInt().toString(),
                    unit = stringResource(R.string.unit_w),
                    icon = Icons.Filled.ElectricBolt,
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = "Пик. ток",
                    value = String.format(Locale.US, "%.1f", currentRide.maxCurrent),
                    unit = stringResource(R.string.unit_a),
                    icon = Icons.Filled.Bolt,
                )
            }
        }

        // Chart section
        if (currentRide.samples.isNotEmpty()) {
            SectionTitle("Графики телеметрии")

            // Scrollable Chips with edge fading
            ScrollableChartChips(
                selectedChart = selectedChart,
                onSelectChart = { selectedChart = it },
                hasTemp = currentRide.samples.any { it.escTemp > 0f },
            )

            // Interactive Main Chart + Range Scrubber
            InteractiveTelemetryChartWithScrubber(
                samples = currentRide.samples,
                chartType = selectedChart,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ScrollableChartChips(
    selectedChart: ChartType,
    onSelectChart: (ChartType) -> Unit,
    hasTemp: Boolean,
) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedChart == ChartType.SPEED,
                onClick = { onSelectChart(ChartType.SPEED) },
                label = { Text(stringResource(R.string.chart_speed)) },
            )
            FilterChip(
                selected = selectedChart == ChartType.POWER,
                onClick = { onSelectChart(ChartType.POWER) },
                label = { Text("Мощность") },
            )
            FilterChip(
                selected = selectedChart == ChartType.CURRENT,
                onClick = { onSelectChart(ChartType.CURRENT) },
                label = { Text("Ток") },
            )
            FilterChip(
                selected = selectedChart == ChartType.BATTERY,
                onClick = { onSelectChart(ChartType.BATTERY) },
                label = { Text("Заряд (%)") },
            )
            FilterChip(
                selected = selectedChart == ChartType.VOLTAGE,
                onClick = { onSelectChart(ChartType.VOLTAGE) },
                label = { Text(stringResource(R.string.chart_voltage)) },
            )
            if (hasTemp) {
                FilterChip(
                    selected = selectedChart == ChartType.TEMPERATURE,
                    onClick = { onSelectChart(ChartType.TEMPERATURE) },
                    label = { Text("Температура") },
                )
            }
        }

        // Left edge fade shadow
        if (scrollState.value > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.background, Color.Transparent)
                        )
                    )
            )
        }

        // Right edge fade shadow
        if (scrollState.value < scrollState.maxValue) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )
        }
    }
}

@Composable
private fun RideSummaryCard(ride: RideSession) {
    val dateFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault())
    val dateText = dateFormat.format(Date(ride.startTime))

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = ride.deviceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Дистанция",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", ride.distanceKm)} км",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Column {
                    Text(
                        text = "Длительность",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDurationFull(ride.durationSeconds),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Расход батареи",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "-${ride.batteryConsumed}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (ride.batteryConsumed > 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveTelemetryChartWithScrubber(
    samples: List<RideSample>,
    chartType: ChartType,
) {
    if (samples.isEmpty()) return

    // Visible range window [0f .. 1f]
    var rangeStart by remember(samples) { mutableFloatStateOf(0f) }
    var rangeEnd by remember(samples) { mutableFloatStateOf(1f) }

    // Slice samples based on range
    val startIndex = (rangeStart * (samples.size - 1)).toInt().coerceIn(0, samples.size - 1)
    val endIndex = (rangeEnd * (samples.size - 1)).toInt().coerceIn(startIndex + 1, samples.size)
    val visibleSamples = remember(samples, startIndex, endIndex) {
        samples.subList(startIndex, endIndex)
    }

    val visibleValues = remember(visibleSamples, chartType) {
        when (chartType) {
            ChartType.SPEED -> visibleSamples.map { it.speed }
            ChartType.POWER -> visibleSamples.map { it.powerWatts }
            ChartType.CURRENT -> visibleSamples.map { it.current }
            ChartType.VOLTAGE -> visibleSamples.map { it.voltage }
            ChartType.BATTERY -> visibleSamples.map { s ->
                if (s.battery >= 0) s.battery.toFloat()
                else ((s.voltage - 31.0f) / (41.8f - 31.0f) * 100f).coerceIn(0f, 100f)
            }
            ChartType.TEMPERATURE -> visibleSamples.map { it.escTemp }
        }
    }

    val allValues = remember(samples, chartType) {
        when (chartType) {
            ChartType.SPEED -> samples.map { it.speed }
            ChartType.POWER -> samples.map { it.powerWatts }
            ChartType.CURRENT -> samples.map { it.current }
            ChartType.VOLTAGE -> samples.map { it.voltage }
            ChartType.BATTERY -> samples.map { s ->
                if (s.battery >= 0) s.battery.toFloat()
                else ((s.voltage - 31.0f) / (41.8f - 31.0f) * 100f).coerceIn(0f, 100f)
            }
            ChartType.TEMPERATURE -> samples.map { it.escTemp }
        }
    }

    val unit = when (chartType) {
        ChartType.SPEED -> "км/ч"
        ChartType.POWER -> "Вт"
        ChartType.CURRENT -> "А"
        ChartType.VOLTAGE -> "V"
        ChartType.BATTERY -> "%"
        ChartType.TEMPERATURE -> "°C"
    }

    val primaryColor = when (chartType) {
        ChartType.SPEED -> MaterialTheme.colorScheme.primary
        ChartType.POWER -> MaterialTheme.colorScheme.tertiary
        ChartType.CURRENT -> MaterialTheme.colorScheme.secondary
        ChartType.VOLTAGE -> Color(0xFF00B4D8)
        ChartType.BATTERY -> Color(0xFF4CAF50)
        ChartType.TEMPERATURE -> Color(0xFFFF6B6B)
    }

    val maxValue = when (chartType) {
        ChartType.BATTERY -> 100f
        else -> (visibleValues.maxOrNull() ?: 10f).coerceAtLeast(1f)
    }
    val minValue = when (chartType) {
        ChartType.VOLTAGE -> ((visibleValues.minOrNull() ?: 30f) - 1f).coerceAtLeast(0f)
        ChartType.BATTERY -> 0f
        else -> 0f
    }

    var touchIndex by remember { mutableIntStateOf(-1) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Fixed height scrub header to avoid vertical jumping
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (touchIndex in visibleSamples.indices) {
                    val activeSample = visibleSamples[touchIndex]
                    val activeVal = visibleValues[touchIndex]
                    val timeFromStart = (activeSample.timestamp - samples.first().timestamp) / 1000L
                    val timeOfDay = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(activeSample.timestamp))

                    Column {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", activeVal)} $unit",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = primaryColor,
                            lineHeight = 28.sp,
                        )
                        Text(
                            text = "Время: ${formatDurationFull(timeFromStart)} ($timeOfDay)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = "Максимум: ${String.format(Locale.US, "%.1f", maxValue)} $unit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 28.sp,
                        )
                        Text(
                            text = "Проведите пальцем по графику для замеров",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(visibleSamples) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                touchIndex = ((offset.x / size.width) * (visibleSamples.size - 1))
                                    .toInt()
                                    .coerceIn(0, visibleSamples.size - 1)
                            },
                            onDragEnd = { touchIndex = -1 },
                            onDragCancel = { touchIndex = -1 },
                            onDrag = { change, _ ->
                                touchIndex = ((change.position.x / size.width) * (visibleSamples.size - 1))
                                    .toInt()
                                    .coerceIn(0, visibleSamples.size - 1)
                            }
                        )
                    }
                    .pointerInput(visibleSamples) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchIndex = ((offset.x / size.width) * (visibleSamples.size - 1))
                                    .toInt()
                                    .coerceIn(0, visibleSamples.size - 1)
                                tryAwaitRelease()
                                touchIndex = -1
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val range = (maxValue - minValue).coerceAtLeast(0.001f)

                    if (visibleValues.size < 2) return@Canvas

                    // Guide grid lines
                    val gridColor = Color.White.copy(alpha = 0.08f)
                    drawLine(gridColor, Offset(0f, 0f), Offset(width, 0f), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, height / 2f), Offset(width, height / 2f), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, height), Offset(width, height), strokeWidth = 1f)

                    // Draw Smooth Curve
                    val stepX = width / (visibleValues.size - 1)
                    val path = Path()
                    val fillPath = Path()

                    visibleValues.forEachIndexed { i, v ->
                        val x = i * stepX
                        val normalized = ((v - minValue) / range).coerceIn(0f, 1f)
                        val y = height - (normalized * (height - 16f)) - 8f

                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevNorm = ((visibleValues[i - 1] - minValue) / range).coerceIn(0f, 1f)
                            val prevY = height - (prevNorm * (height - 16f)) - 8f

                            val cx1 = (prevX + x) / 2f
                            val cy1 = prevY
                            val cx2 = (prevX + x) / 2f
                            val cy2 = y

                            path.cubicTo(cx1, cy1, cx2, cy2, x, y)
                            fillPath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                        }

                        if (i == visibleValues.size - 1) {
                            fillPath.lineTo(x, height)
                            fillPath.close()
                        }
                    }

                    // Gradient fill under the line
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = height,
                        )
                    )

                    // Line stroke
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        )
                    )

                    // Touch scrubber line and point
                    if (touchIndex in visibleValues.indices) {
                        val touchX = touchIndex * stepX
                        val normalized = ((visibleValues[touchIndex] - minValue) / range).coerceIn(0f, 1f)
                        val touchY = height - (normalized * (height - 16f)) - 8f

                        drawLine(
                            color = primaryColor.copy(alpha = 0.8f),
                            start = Offset(touchX, 0f),
                            end = Offset(touchX, height),
                            strokeWidth = 2.dp.toPx(),
                        )

                        drawCircle(
                            color = primaryColor.copy(alpha = 0.3f),
                            radius = 9.dp.toPx(),
                            center = Offset(touchX, touchY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = Offset(touchX, touchY)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Telegram-style Mini Range Overview Scrubber (if enough samples)
            if (samples.size > 8) {
                Text(
                    text = "Выбор области просмотра",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                MiniRangeOverview(
                    allValues = allValues,
                    primaryColor = primaryColor,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    onRangeChanged = { start, end ->
                        rangeStart = start
                        rangeEnd = end
                    }
                )
            }
        }
    }
}

@Composable
private fun MiniRangeOverview(
    allValues: List<Float>,
    primaryColor: Color,
    rangeStart: Float,
    rangeEnd: Float,
    onRangeChanged: (Float, Float) -> Unit,
) {
    val maxVal = (allValues.maxOrNull() ?: 1f).coerceAtLeast(0.01f)
    val minVal = (allValues.minOrNull() ?: 0f).coerceAtLeast(0f)
    val range = (maxVal - minVal).coerceAtLeast(0.001f)

    val currentRangeStart by rememberUpdatedState(rangeStart)
    val currentRangeEnd by rememberUpdatedState(rangeEnd)
    val currentOnRangeChanged by rememberUpdatedState(onRangeChanged)

    var dragMode by remember { mutableIntStateOf(0) } // 0: none, 1: left handle, 2: right handle, 3: middle window
    var dragStartOffset by remember { mutableFloatStateOf(0f) }
    var initialStart by remember { mutableFloatStateOf(0f) }
    var initialEnd by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        val xRatio = (offset.x / width).coerceIn(0f, 1f)
                        val handleTouchRange = 36.dp.toPx() / width

                        initialStart = currentRangeStart
                        initialEnd = currentRangeEnd
                        dragStartOffset = offset.x

                        dragMode = when {
                            kotlin.math.abs(xRatio - currentRangeStart) < handleTouchRange -> 1
                            kotlin.math.abs(xRatio - currentRangeEnd) < handleTouchRange -> 2
                            xRatio in currentRangeStart..currentRangeEnd -> 3
                            else -> {
                                val windowWidth = (currentRangeEnd - currentRangeStart).coerceIn(0.12f, 1f)
                                val newStart = (xRatio - windowWidth / 2f).coerceIn(0f, 1f - windowWidth)
                                val newEnd = (newStart + windowWidth).coerceIn(0f, 1f)
                                initialStart = newStart
                                initialEnd = newEnd
                                currentOnRangeChanged(newStart, newEnd)
                                3
                            }
                        }
                    },
                    onDragEnd = { dragMode = 0 },
                    onDragCancel = { dragMode = 0 },
                    onDrag = { change, _ ->
                        val width = size.width
                        val totalDxRatio = (change.position.x - dragStartOffset) / width
                        val minWindow = 0.08f

                        when (dragMode) {
                            1 -> {
                                val newStart = (initialStart + totalDxRatio).coerceIn(0f, initialEnd - minWindow)
                                currentOnRangeChanged(newStart, initialEnd)
                            }
                            2 -> {
                                val newEnd = (initialEnd + totalDxRatio).coerceIn(initialStart + minWindow, 1f)
                                currentOnRangeChanged(initialStart, newEnd)
                            }
                            3 -> {
                                val windowWidth = initialEnd - initialStart
                                val newStart = (initialStart + totalDxRatio).coerceIn(0f, 1f - windowWidth)
                                currentOnRangeChanged(newStart, newStart + windowWidth)
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Draw smoothed full waveform fill and stroke
            if (allValues.size >= 2) {
                val stepX = width / (allValues.size - 1)
                val path = Path()
                val fillPath = Path()

                allValues.forEachIndexed { i, v ->
                    val x = i * stepX
                    val norm = ((v - minVal) / range).coerceIn(0f, 1f)
                    val y = height - (norm * (height - 12f)) - 6f

                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (i - 1) * stepX
                        val prevNorm = ((allValues[i - 1] - minVal) / range).coerceIn(0f, 1f)
                        val prevY = height - (prevNorm * (height - 12f)) - 6f

                        val cx1 = (prevX + x) / 2f
                        val cy1 = prevY
                        val cx2 = (prevX + x) / 2f
                        val cy2 = y

                        path.cubicTo(cx1, cy1, cx2, cy2, x, y)
                        fillPath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                    }

                    if (i == allValues.size - 1) {
                        fillPath.lineTo(x, height)
                        fillPath.close()
                    }
                }

                // Fill under mini waveform
                drawPath(
                    path = fillPath,
                    color = primaryColor.copy(alpha = 0.22f)
                )

                // Mini waveform outline
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.6f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 2. Dim areas outside selected range
            val startX = rangeStart * width
            val endX = rangeEnd * width
            val dimColor = Color.Black.copy(alpha = 0.55f)

            if (startX > 0f) {
                drawRect(
                    color = dimColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(startX, height)
                )
            }
            if (endX < width) {
                drawRect(
                    color = dimColor,
                    topLeft = Offset(endX, 0f),
                    size = Size(width - endX, height)
                )
            }

            // 3. Thick Material 3 Expressive Range Frame
            val strokeWidth = 3.5.dp.toPx()
            val halfStroke = strokeWidth / 2f
            val cornerRadius = CornerRadius(14.dp.toPx())

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(startX + halfStroke, halfStroke),
                size = Size((endX - startX - strokeWidth).coerceAtLeast(0f), height - strokeWidth),
                cornerRadius = cornerRadius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 4. White Pill Handles inside frame with clean padding
            val handleWidth = 4.dp.toPx()
            val handleHeight = 20.dp.toPx()
            val handleCorner = CornerRadius(2.dp.toPx())
            val handleTop = (height - handleHeight) / 2f
            val handlePadding = 7.dp.toPx()

            // Left handle
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(startX + handlePadding, handleTop),
                size = Size(handleWidth, handleHeight),
                cornerRadius = handleCorner,
            )

            // Right handle
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(endX - handlePadding - handleWidth, handleTop),
                size = Size(handleWidth, handleHeight),
                cornerRadius = handleCorner,
            )
        }
    }
}

private fun formatDurationFull(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}ч ${mins}м ${secs}с"
        mins > 0 -> "${mins}м ${secs}с"
        else -> "${secs}с"
    }
}
