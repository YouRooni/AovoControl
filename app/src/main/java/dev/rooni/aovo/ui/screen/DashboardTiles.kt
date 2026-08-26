package dev.rooni.aovo.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rooni.aovo.R
import dev.rooni.aovo.ble.ConnectionState
import dev.rooni.aovo.ble.RideState
import dev.rooni.aovo.ble.Telemetry
import dev.rooni.aovo.ble.ViContDecoder
import dev.rooni.aovo.data.DashboardTile
import dev.rooni.aovo.data.GaugeStyle
import dev.rooni.aovo.data.ProfileIcons
import dev.rooni.aovo.data.RideProfile
import dev.rooni.aovo.data.TileType
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.Haptic
import dev.rooni.aovo.ui.LocalHaptics
import dev.rooni.aovo.ui.component.ActionTile
import dev.rooni.aovo.ui.component.MetricCard
import dev.rooni.aovo.ui.component.SectionDefaults
import dev.rooni.aovo.ui.component.SpeedGauge
import dev.rooni.aovo.ui.component.TileMetrics

data class TileContext(
    val viewModel: AovoViewModel,
    val connection: ConnectionState,
    val telemetry: Telemetry,
    val ride: RideState,
    val deviceName: String,
    val gears: List<Int> = listOf(1, 2, 3),
    val sensors: ViContDecoder.Sensors? = null,
    val onOpenDevices: () -> Unit,
    val editing: Boolean = false,
    val gaugeStyle: GaugeStyle = GaugeStyle.CLASSIC,
) {
    val connected: Boolean get() = connection == ConnectionState.CONNECTED
}

/** Whether an action tile is currently switched on, which decides its corner radius. */
fun tileIsActive(tile: DashboardTile, context: TileContext): Boolean = when (tile.type) {
    TileType.Headlight -> context.ride.headLight
    TileType.AmbientLight -> context.ride.ambientLight
    TileType.Cruise -> context.ride.cruiseControl
    TileType.Lock -> context.ride.locked
    else -> false
}

/** True for the tiles that behave as switches and therefore change shape with their state. */
fun TileType.isActionTile(): Boolean = this == TileType.Headlight ||
    this == TileType.AmbientLight || this == TileType.Cruise ||
    this == TileType.Lock || this == TileType.Profile || this == TileType.FindScooter

/** Human-readable name for the tile picker and the edit overlay. */
@Composable
fun tileLabel(type: TileType, profileName: String?): String = when (type) {
    TileType.Gauge -> stringResource(R.string.tile_gauge)
    TileType.Connection -> stringResource(R.string.tile_connection)
    TileType.ModeSelector -> stringResource(R.string.tile_mode)
    TileType.Headlight -> stringResource(R.string.headlight)
    TileType.AmbientLight -> stringResource(R.string.ambient_light)
    TileType.Cruise -> stringResource(R.string.cruise)
    TileType.Lock -> stringResource(R.string.lock)
    TileType.FindScooter -> stringResource(R.string.find_scooter)
    TileType.Speed -> stringResource(R.string.speed)
    TileType.SpeedLimit -> stringResource(R.string.tile_speed_limit)
    TileType.Battery -> stringResource(R.string.battery)
    TileType.Voltage -> stringResource(R.string.voltage)
    TileType.Current -> stringResource(R.string.current)
    TileType.Power -> stringResource(R.string.power)
    TileType.EscTemperature -> stringResource(R.string.controller_temp)
    TileType.MotorTemperature -> stringResource(R.string.motor_temp)
    TileType.MotorRpm -> stringResource(R.string.motor_rpm)
    TileType.Throttle -> stringResource(R.string.throttle)
    TileType.Brake -> stringResource(R.string.brake)
    TileType.Trip -> stringResource(R.string.trip)
    TileType.Odometer -> stringResource(R.string.odometer)
    TileType.ServiceDue -> stringResource(R.string.service_due)
    TileType.Profile -> profileName ?: stringResource(R.string.profile)
    TileType.Spacer -> stringResource(R.string.tile_spacer)
}

private fun DashboardTile.resolvedHeight(): Dp =
    TileMetrics.MinHeight + HEIGHT_STEP * (clampedHeight() - 1)

/** A spacer is pure padding, so its first step is much smaller than a real tile. */
private fun DashboardTile.spacerHeight(): Dp = SPACER_STEP * clampedHeight()

private val HEIGHT_STEP = 80.dp
private val SPACER_STEP = 26.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardTileContent(
    tile: DashboardTile,
    shape: RoundedCornerShape,
    context: TileContext,
) {
    val ride = context.ride
    val telemetry = context.telemetry
    val imperial = ride.imperial
    val distanceUnit = stringResource(if (imperial) R.string.unit_mi else R.string.unit_km)
    val speedUnit = stringResource(if (imperial) R.string.unit_mph else R.string.unit_kmh)
    when (tile.type) {
        TileType.Spacer -> Box(Modifier.fillMaxWidth().height(tile.spacerHeight()))

        TileType.Connection -> ConnectionTile(tile, shape, context)

        TileType.Gauge -> {
            val gearLimit = activeGearLimit(ride)
            // The gauge owns its whole tile and sits in the middle of it, so growing the
            // tile makes the dial bigger instead of leaving it stranded at the top.
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().height(tile.resolvedHeight()),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SpeedGauge(
                        speed = telemetry.speed,
                        maxSpeed = maxOf(gearLimit, ride.limitSport, 25).toFloat(),
                        battery = telemetry.battery,
                        unit = speedUnit,
                        subtitle = if (context.connected) {
                            telemetry.battery.toString() + "% · " + telemetry.voltage + " V"
                        } else {
                            stringResource(R.string.disconnected)
                        },
                        active = context.connected,
                        style = context.gaugeStyle,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        TileType.ModeSelector -> ModeSelectorTile(context)

        TileType.Headlight -> ActionTile(
            shape = shape,
            label = stringResource(R.string.headlight),
            icon = Icons.Filled.Lightbulb,
            selected = ride.headLight,
            enabled = context.connected,
            interactive = !context.editing,
            showLabel = tile.showsLabel,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
            onClick = { context.viewModel.core.setHeadLight(!ride.headLight) },
        )

        TileType.AmbientLight -> ActionTile(
            shape = shape,
            label = stringResource(R.string.ambient_light),
            icon = Icons.Filled.Nightlight,
            selected = ride.ambientLight,
            enabled = context.connected,
            interactive = !context.editing,
            showLabel = tile.showsLabel,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
            onClick = { context.viewModel.core.setAmbientLight(!ride.ambientLight) },
        )

        TileType.Cruise -> ActionTile(
            shape = shape,
            label = stringResource(R.string.cruise),
            icon = Icons.Filled.Speed,
            selected = ride.cruiseControl,
            enabled = context.connected,
            interactive = !context.editing,
            showLabel = tile.showsLabel,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
            onClick = { context.viewModel.core.setCruiseControl(!ride.cruiseControl) },
        )

        TileType.Lock -> LockTile(tile, shape, context)
        TileType.FindScooter -> FindScooterTile(tile, shape, context)

        TileType.Speed -> MetricCard(
            shape = shape,
            label = stringResource(R.string.speed),
            value = format1(telemetry.speed),
            unit = speedUnit,
            icon = Icons.Filled.Speed,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.SpeedLimit -> {
            val limit = activeGearLimit(ride)
            val labels = modeLabels()
            val modeLabel = labels[ride.gear.coerceIn(0, labels.lastIndex)]
            MetricCard(
                shape = shape,
                label = if (tile.showsLabel) {
                    stringResource(R.string.tile_speed_limit_mode, modeLabel)
                } else {
                    stringResource(R.string.tile_speed_limit)
                },
                // A zero means the dashboard has not reported its caps yet, which is not
                // the same as a limit of zero and must not be shown as one.
                value = if (context.connected && limit > 0) limit.toString() else "—",
                unit = stringResource(R.string.unit_kmh),
                icon = Icons.Filled.Speed,
                modifier = Modifier.heightIn(min = tile.resolvedHeight()),
            )
        }

        TileType.Battery -> MetricCard(
            shape = shape,
            label = stringResource(R.string.battery),
            value = telemetry.battery.toString(),
            unit = stringResource(R.string.unit_percent),
            icon = Icons.Filled.BatteryFull,
            accent = if (telemetry.battery <= 15) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.Voltage -> MetricCard(
            shape = shape,
            label = stringResource(R.string.voltage),
            value = format1(telemetry.voltage),
            unit = stringResource(R.string.unit_v),
            icon = Icons.Filled.ElectricBolt,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.Current -> MetricCard(
            shape = shape,
            label = stringResource(R.string.current),
            value = format1(telemetry.current),
            unit = stringResource(R.string.unit_a),
            icon = Icons.Filled.Bolt,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.MotorRpm -> MetricCard(
            shape = shape,
            label = stringResource(R.string.motor_rpm),
            value = telemetry.motorRpm.toString(),
            unit = stringResource(R.string.unit_rpm),
            icon = Icons.Filled.RotateRight,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        // Shown as a percentage of the sensor's own full travel, which is what 250 means.
        TileType.Throttle -> MetricCard(
            shape = shape,
            label = stringResource(R.string.throttle),
            value = context.sensors?.let { percentOfTravel(it.throttle) } ?: "—",
            unit = stringResource(R.string.unit_percent),
            icon = Icons.Filled.Speed,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.Brake -> {
            val sensors = context.sensors
            MetricCard(
                shape = shape,
                label = stringResource(R.string.brake),
                // A scooter with two levers gets both, separated; one with a single lever
                // reports zero for the absent sensor and would otherwise always read "0 / x".
                value = when {
                    sensors == null -> "—"
                    sensors.hasSecondBrake ->
                        percentOfTravel(sensors.brake1) + " / " + percentOfTravel(sensors.brake2)
                    else -> percentOfTravel(sensors.brake1)
                },
                unit = stringResource(R.string.unit_percent),
                icon = Icons.Filled.DoNotDisturbOn,
                modifier = Modifier.heightIn(min = tile.resolvedHeight()),
            )
        }

        TileType.Power -> MetricCard(
            shape = shape,
            label = stringResource(R.string.power),
            value = format1(telemetry.power),
            unit = stringResource(R.string.unit_w),
            icon = Icons.Filled.Power,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.EscTemperature -> MetricCard(
            shape = shape,
            label = stringResource(R.string.controller_temp),
            value = telemetry.escTemperature.toString(),
            unit = stringResource(R.string.unit_c),
            icon = Icons.Filled.Thermostat,
            accent = if (telemetry.escTemperature >= 70) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.MotorTemperature -> MetricCard(
            shape = shape,
            label = stringResource(R.string.motor_temp),
            value = telemetry.motorTemperature.toString(),
            unit = stringResource(R.string.unit_c),
            icon = Icons.Filled.DeviceThermostat,
            accent = if (telemetry.motorTemperature >= 90) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.Trip -> MetricCard(
            shape = shape,
            label = stringResource(R.string.trip),
            value = format1(telemetry.tripDistance),
            unit = distanceUnit,
            icon = Icons.Filled.Route,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.Odometer -> MetricCard(
            shape = shape,
            label = stringResource(R.string.odometer),
            value = format1(telemetry.totalDistance),
            unit = distanceUnit,
            icon = Icons.Filled.Timeline,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.ServiceDue -> MetricCard(
            shape = shape,
            label = stringResource(R.string.service_due),
            value = serviceRemaining(ride, telemetry),
            unit = distanceUnit,
            icon = Icons.Filled.Build,
            modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        )

        TileType.Profile -> ProfileTile(tile, shape, context)
    }
}

/** Cap in force right now: the per-mode limit for the gear the scooter is riding in. */
internal fun activeGearLimit(ride: RideState): Int = when (ride.gear) {
    4 -> ride.limitGear5
    3 -> ride.limitGear4
    2 -> ride.limitSport
    1 -> ride.limitComfort
    else -> ride.limitEco
}

@Composable
private fun modeLabels(): List<String> = listOf(
    stringResource(R.string.mode_eco),
    stringResource(R.string.mode_drive),
    stringResource(R.string.mode_sport),
    stringResource(R.string.mode_walk),
    stringResource(R.string.mode_gear5),
)

/** Sensor travel as a percentage; the dashboard normalises a full pull to 250. */
private fun percentOfTravel(raw: Int): String =
    (raw.coerceIn(0, SENSOR_FULL_TRAVEL) * 100 / SENSOR_FULL_TRAVEL).toString()

private const val SENSOR_FULL_TRAVEL = 250

private fun serviceRemaining(ride: RideState, telemetry: Telemetry): String {
    val next = ride.lastServiceMileage + ride.serviceMileage
    val left = next - telemetry.totalDistance
    return if (left <= 0) "0" else left.toInt().toString()
}

@Composable
private fun ProfileTile(
    tile: DashboardTile,
    shape: RoundedCornerShape,
    context: TileContext,
) {
    val profile: RideProfile? = context.viewModel.profile(tile.profileId)
    ActionTile(
        shape = shape,
        label = profile?.name ?: stringResource(R.string.profile),
        icon = profileIcon(profile?.icon ?: ProfileIcons.DEFAULT),
        selected = false,
        enabled = context.connected && profile != null,
        interactive = !context.editing,
        showLabel = tile.showsLabel,
        modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        onClick = { profile?.let(context.viewModel::applyProfile) },
    )
}

@Composable
private fun FindScooterTile(
    tile: DashboardTile,
    shape: RoundedCornerShape,
    context: TileContext,
) {
    val unsupported = stringResource(R.string.find_scooter_unsupported)
    val haptics = LocalHaptics.current
    ActionTile(
        shape = shape,
        label = stringResource(R.string.find_scooter),
        icon = Icons.Filled.NotificationsActive,
        selected = false,
        enabled = context.connected,
        interactive = !context.editing,
        showLabel = tile.showsLabel,
        modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        onClick = {
            if (context.viewModel.core.findScooter()) {
                haptics?.perform(Haptic.Confirm)
            } else {
                haptics?.perform(Haptic.Reject)
                context.viewModel.notify(unsupported)
            }
        },
    )
}

@Composable
private fun LockTile(
    tile: DashboardTile,
    shape: RoundedCornerShape,
    context: TileContext,
) {
    val ride = context.ride
    val lockWarning = stringResource(R.string.lock_requires_stop)
    val haptics = LocalHaptics.current
    ActionTile(
        shape = shape,
        label = stringResource(if (ride.locked) R.string.locked else R.string.unlocked),
        icon = if (ride.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
        selected = ride.locked,
        enabled = context.connected,
        interactive = !context.editing,
        showLabel = tile.showsLabel,
        modifier = Modifier.heightIn(min = tile.resolvedHeight()),
        onClick = {
            if (!ride.locked && context.telemetry.speed > 1f) {
                haptics?.perform(Haptic.Reject)
                context.viewModel.notify(lockWarning)
            } else {
                context.viewModel.core.setLocked(!ride.locked)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeSelectorTile(context: TileContext) {
    val labels = modeLabels()
    val haptics = LocalHaptics.current
    val gear = context.ride.gear
    // Only the gears this scooter admits: a button for one it would refuse looks live and
    // does nothing.
    val available = context.gears.map { it - 1 }.filter { it in labels.indices }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Three across is what the tile is wide enough to read comfortably. With more gears
        // than that the row keeps the same button size and scrolls, so the fourth sits half
        // in view and says there is more rather than cramming everything into the width.
        val gap = SectionDefaults.Gap
        val visible = minOf(available.size, MODE_COLUMNS)
        val buttonWidth = (maxWidth - gap * (visible - 1)) / visible
        val scrollable = available.size > MODE_COLUMNS

        // Bring the selected gear into view when it changes from the scooter's side too,
        // not only when it is tapped here.
        LaunchedEffect(gear, scrollable, buttonWidth) {
            if (!scrollable) return@LaunchedEffect
            val index = available.indexOf(gear).takeIf { it >= 0 } ?: return@LaunchedEffect
            val target = with(density) { ((buttonWidth + gap) * index).toPx() }
            val centred = target - with(density) { ((maxWidth - buttonWidth) / 2).toPx() }
            scroll.animateScrollTo(
                centred.roundToInt().coerceIn(0, scroll.maxValue),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }

        Row(
            modifier = if (scrollable) {
                Modifier.fillMaxWidth().horizontalScroll(scroll)
            } else {
                Modifier.fillMaxWidth()
            },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            available.forEach { index ->
                ToggleButton(
                    checked = gear == index,
                    onCheckedChange = {
                        if (context.connected && gear != index) {
                            haptics?.perform(Haptic.Heavy)
                            context.viewModel.core.setGear(index)
                        }
                    },
                    enabled = context.connected && !context.editing,
                    shapes = ToggleButtonDefaults.shapes(),
                    modifier = Modifier.width(buttonWidth).height(54.dp),
                ) {
                    Text(
                        text = labels[index],
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Gear buttons shown at once before the row starts scrolling. */
private const val MODE_COLUMNS = 3

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectionTile(
    tile: DashboardTile,
    shape: RoundedCornerShape,
    context: TileContext,
) {
    val connection = context.connection
    val busy = connection == ConnectionState.CONNECTING ||
        connection == ConnectionState.AUTHENTICATING ||
        connection == ConnectionState.SCANNING
    val connected = context.connected

    val displayName = if (connected && context.deviceName.isNotBlank()) {
        context.deviceName
    } else {
        stringResource(if (connected) R.string.connected else R.string.disconnected)
    }

    val isCompact = tile.clampedSpan() <= 2
    val animatedHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isCompact) TileMetrics.MinHeight else 74.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "connectionTileHeight",
    )

    Surface(
        shape = shape,
        color = if (connected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .then(
                if (!context.editing) {
                    Modifier.clickable {
                        if (connected) context.viewModel.disconnect() else context.onOpenDevices()
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        if (isCompact) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (busy) {
                            LoadingIndicator(modifier = Modifier.size(22.dp))
                        } else {
                            Icon(
                                imageVector = if (connected) Icons.Filled.BluetoothConnected
                                else Icons.Filled.BluetoothDisabled,
                                contentDescription = null,
                                tint = if (connected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = if (connected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = if (connected) stringResource(R.string.ready_to_ride)
                        else if (busy) stringResource(R.string.connecting)
                        else stringResource(R.string.tap_to_connect),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = if (connected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (busy) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = if (connected) Icons.Filled.BluetoothConnected
                                else Icons.Filled.BluetoothDisabled,
                                contentDescription = null,
                                tint = if (connected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (connected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = if (connected) {
                            stringResource(R.string.ready_to_ride) + " · " + context.telemetry.battery + "%"
                        } else if (busy) {
                            stringResource(R.string.connecting)
                        } else {
                            stringResource(R.string.tap_to_connect)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                FilledTonalButton(
                    enabled = !context.editing,
                    onClick = if (connected) {
                        { context.viewModel.disconnect() }
                    } else {
                        context.onOpenDevices
                    },
                ) {
                    Text(
                        text = stringResource(if (connected) R.string.disconnect else R.string.connect),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

internal fun format1(value: Float): String =
    String.format(java.util.Locale.US, "%.1f", value)
