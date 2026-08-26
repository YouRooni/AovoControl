package dev.rooni.aovo.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.R
import dev.rooni.aovo.ride.OverallRideStats
import dev.rooni.aovo.ride.RideSession
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.component.MetricCard
import dev.rooni.aovo.ui.component.SectionGrid
import dev.rooni.aovo.ui.component.SectionTitle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RidesScreen(
    viewModel: AovoViewModel,
    onOpenRide: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rides by viewModel.rideHistory.collectAsStateWithLifecycle()
    val stats by viewModel.rideStats.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRideRecording.collectAsStateWithLifecycle()

    var showClearConfirm by remember { mutableStateOf(false) }
    var rideToDelete by remember { mutableStateOf<RideSession?>(null) }

    val groupedRides = remember(rides) {
        groupRidesByDay(rides)
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRideHistory()
                        showClearConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    rideToDelete?.let { ride ->
        AlertDialog(
            onDismissRequest = { rideToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_ride_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRide(ride.id)
                        rideToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { rideToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Active recording banner
        if (isRecording) {
            item {
                ActiveRecordingBanner()
            }
        }

        // Stats summary cards
        if (stats.totalRides > 0) {
            item {
                OverallStatsHeader(stats = stats, onClear = { showClearConfirm = true })
            }
        }

        if (rides.isEmpty() && !isRecording) {
            item {
                EmptyRidesCard()
            }
        } else {
            groupedRides.forEach { (header, items) ->
                item {
                    SectionTitle(header)
                }
                items(items, key = { it.id }) { ride ->
                    RideCard(
                        ride = ride,
                        onClick = { onOpenRide(ride.id) },
                        onDelete = { rideToDelete = ride },
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ActiveRecordingBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.recording_ride),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Телеметрия пишется в реальном времени",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun OverallStatsHeader(stats: OverallRideStats, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(stringResource(R.string.rides_title))
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.clear_history),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        SectionGrid {
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.stat_total_distance),
                    value = String.format(Locale.US, "%.1f", stats.totalDistanceKm),
                    unit = stringResource(R.string.unit_km),
                    icon = Icons.Filled.Route,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.stat_total_time),
                    value = formatDurationHoursMinutes(stats.totalDurationSeconds),
                    unit = "",
                    icon = Icons.Filled.Timer,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.stat_top_speed),
                    value = String.format(Locale.US, "%.1f", stats.maxSpeedRecord),
                    unit = stringResource(R.string.unit_kmh),
                    icon = Icons.Filled.Speed,
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.stat_top_power),
                    value = stats.maxPowerRecord.toInt().toString(),
                    unit = stringResource(R.string.unit_w),
                    icon = Icons.Filled.ElectricBolt,
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RideCard(
    ride: RideSession,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: Time range and Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${formatTime(ride.startTime)} · ${formatDuration(ride.durationSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle row: Distance prominence and battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.2f", ride.distanceKm),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.unit_km),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (ride.endBattery <= 20) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (ride.endBattery <= 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "-${ride.batteryConsumed}%  (${ride.endBattery}%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom row metrics: speed, peak current / power, and arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Ср. ${formatSpeed1(ride.avgSpeed)} км/ч  ·  Макс. ${formatSpeed1(ride.maxSpeed)} км/ч",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (ride.maxPowerWatts > 0f) {
                        Text(
                            text = "Пик: ${ride.maxPowerWatts.toInt()} Вт (${formatSpeed1(ride.maxCurrent)} А)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun EmptyRidesCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsBike,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
            Text(
                text = stringResource(R.string.no_rides),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun groupRidesByDay(rides: List<RideSession>): Map<String, List<RideSession>> {
    val result = LinkedHashMap<String, MutableList<RideSession>>()
    val now = Calendar.getInstance()
    val todayDay = now.get(Calendar.DAY_OF_YEAR)
    val todayYear = now.get(Calendar.YEAR)

    val dateFormat = SimpleDateFormat("d MMMM", Locale.getDefault())

    for (ride in rides) {
        val cal = Calendar.getInstance().apply { timeInMillis = ride.startTime }
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)

        val label = when {
            year == todayYear && day == todayDay -> "Сегодня"
            year == todayYear && day == todayDay - 1 -> "Вчера"
            else -> dateFormat.format(Date(ride.startTime))
        }

        result.getOrPut(label) { ArrayList() }.add(ride)
    }

    return result
}

private fun formatTime(millis: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins} мин ${secs} сек" else "${secs} сек"
}

private fun formatDurationHoursMinutes(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hours > 0) "${hours}ч ${mins}м" else "${mins} мин"
}

private fun formatSpeed1(v: Float): String = String.format(Locale.US, "%.1f", v)
