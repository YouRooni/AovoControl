package dev.rooni.aovo.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.R
import kotlin.math.roundToInt
import dev.rooni.aovo.ble.ConnectionState
import dev.rooni.aovo.ble.ScooterFamily
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.Haptic
import dev.rooni.aovo.ui.LocalHaptics
import dev.rooni.aovo.ui.component.Section
import dev.rooni.aovo.ui.component.SectionTitle
import dev.rooni.aovo.ui.component.SettingRow
import dev.rooni.aovo.ui.component.SwitchRow

@Composable
fun RideSettingsScreen(viewModel: AovoViewModel, modifier: Modifier = Modifier) {
    val ride by viewModel.core.ride.collectAsStateWithLifecycle()
    val family by viewModel.core.family.collectAsStateWithLifecycle()
    val gears by viewModel.core.gears.collectAsStateWithLifecycle()
    val connection by viewModel.core.connection.collectAsStateWithLifecycle()
    val telemetry by viewModel.core.telemetry.collectAsStateWithLifecycle()
    val connected = connection == ConnectionState.CONNECTED
    val core = viewModel.core

    // under way they are acknowledged and then ridden straight past.
    val movingLock = connected && telemetry.settingsLocked

    var choice by remember { mutableStateOf<Choice?>(null) }

    // as long as the screen is open and is otherwise inferred from the on/off flag.
    var lampMode by remember(ride.ambientLight) { mutableStateOf(if (ride.ambientLight) 3 else 0) }
    var lampHue by remember { mutableStateOf(AmbientColours.first().hue) }

    // fourth and fifth here the walking gear fell off the end of the list and read as Eco.
    val modeLabels = listOf(
        stringResource(R.string.mode_eco),
        stringResource(R.string.mode_drive),
        stringResource(R.string.mode_sport),
        stringResource(R.string.mode_walk),
        stringResource(R.string.mode_gear5),
    )

    // Only the gears this scooter admits: offering one it would refuse is worse than not
    // offering it, because the row would appear to do nothing.
    val selectableGears = gears.map { it - 1 }.filter { it in modeLabels.indices }

    Column(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        SectionTitle(stringResource(R.string.ride_settings))
        Section {
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.cruise_control),
                    subtitle = stringResource(R.string.cruise_control_info),
                    icon = Icons.Filled.Speed,
                    checked = ride.cruiseControl,
                    enabled = connected,
                    onCheckedChange = core::setCruiseControl,
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.riding_mode),
                    value = modeLabels.getOrElse(ride.gear) { "—" },
                    icon = Icons.AutoMirrored.Filled.DirectionsBike,
                    enabled = connected,
                    onClick = { choice = Choice.Mode },
                )
            }
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.headlight),
                    icon = Icons.Filled.Lightbulb,
                    checked = ride.headLight,
                    enabled = connected,
                    onCheckedChange = core::setHeadLight,
                )
            }
            if (family == ScooterFamily.VICONT) {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.ambient_light_mode),
                        value = ambientModeLabels()[lampMode.coerceIn(0, 4)],
                        icon = Icons.Filled.Nightlight,
                        enabled = connected,
                        onClick = { choice = Choice.AmbientMode },
                    )
                }
                tileIf(lampMode == 3 || lampMode == 4) { shape ->
                    ColourRow(
                        shape = shape,
                        selected = lampHue,
                        enabled = connected,
                    ) { hue ->
                        lampHue = hue
                        core.setAmbienceLamp(lampMode + 1, hue)
                    }
                }
            } else {
                tile { shape ->
                    SwitchRow(
                        shape = shape,
                        title = stringResource(R.string.ambient_light),
                        icon = Icons.Filled.Nightlight,
                        checked = ride.ambientLight,
                        enabled = connected,
                        onCheckedChange = core::setAmbientLight,
                    )
                }
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.units),
                    value = stringResource(if (ride.imperial) R.string.unit_mph_long else R.string.unit_kmh_long),
                    icon = Icons.Filled.Straighten,
                    enabled = connected,
                    onClick = { choice = Choice.Units },
                )
            }
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.zero_start),
                    subtitle = stringResource(R.string.zero_start_desc),
                    icon = Icons.Filled.Bolt,
                    checked = ride.zeroStart,
                    enabled = connected,
                    onCheckedChange = core::setZeroStart,
                )
            }
        }

        SectionTitle(stringResource(R.string.speed_limits))
        if (movingLock) MovingLockWarning()
        // A gear limit is real up to what the motor can manage, and cosmetic beyond it.
        // of 35 at 643 rpm, so the cap genuinely holds the motor back. But 643 rpm is also
        // where that motor runs out, so a sport limit of 60 left the rpm alone and only
        // stretched the reading, from 35 km/h to 60.
        if (family == ScooterFamily.VICONT) {
            NoticeCard(text = stringResource(R.string.limits_scale_display))
        }
        Section {
            tile { shape ->
                LimitSlider(
                    shape = shape,
                    label = stringResource(R.string.limit_eco),
                    value = ride.limitEco,
                    enabled = connected && !movingLock,
                ) { core.setModeLimits(ride.limitCruise, it, ride.limitComfort, ride.limitSport) }
            }
            tile { shape ->
                LimitSlider(
                    shape = shape,
                    label = stringResource(R.string.limit_drive),
                    value = ride.limitComfort,
                    enabled = connected && !movingLock,
                ) { core.setModeLimits(ride.limitCruise, ride.limitEco, it, ride.limitSport) }
            }
            tile { shape ->
                LimitSlider(
                    shape = shape,
                    label = stringResource(R.string.limit_sport),
                    value = ride.limitSport,
                    enabled = connected && !movingLock,
                ) { core.setModeLimits(ride.limitCruise, ride.limitEco, ride.limitComfort, it) }
            }
            // their caps have no read command, so a slider appears once there is a value —
            // either read from the limit frame or written here.
            tileIf(4 in gears) { shape ->
                LimitSlider(
                    shape = shape,
                    label = stringResource(R.string.limit_walk),
                    value = ride.limitGear4,
                    enabled = connected && !movingLock,
                ) { core.setGearLimit(4, it) }
            }
            tileIf(5 in gears) { shape ->
                LimitSlider(
                    shape = shape,
                    label = stringResource(R.string.limit_gear5),
                    value = ride.limitGear5,
                    enabled = connected && !movingLock,
                ) { core.setGearLimit(5, it) }
            }
            tileIf(family != ScooterFamily.VICONT) { shape ->
                LimitSlider(
                    shape = shape,
                    label = stringResource(R.string.limit_cruise),
                    value = ride.limitCruise,
                    enabled = connected && !movingLock,
                ) { core.setModeLimits(it, ride.limitEco, ride.limitComfort, ride.limitSport) }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }

    if (choice == Choice.AmbientMode) {
        ChoiceDialog(
            title = stringResource(R.string.ambient_light_mode),
            options = ambientModeLabels(),
            selected = lampMode,
            onDismiss = { choice = null },
        ) { picked ->
            lampMode = picked
            // Modes are one-based on the wire; zero is reserved for asking.
            core.setAmbienceLamp(picked + 1, lampHue)
            choice = null
        }
    }

    if (choice == Choice.Mode) {
        ChoiceDialog(
            title = stringResource(R.string.riding_mode),
            options = selectableGears.map { modeLabels[it] },
            selected = selectableGears.indexOf(ride.gear).coerceAtLeast(0),
            onDismiss = { choice = null },
        ) { picked ->
            selectableGears.getOrNull(picked)?.let { core.setGear(it) }
            choice = null
        }
    }

    if (choice == Choice.Units) {
        val unitOptions = listOf(
            stringResource(R.string.unit_kmh_long),
            stringResource(R.string.unit_mph_long),
        )
        ChoiceDialog(
            title = stringResource(R.string.units),
            options = unitOptions,
            selected = if (ride.imperial) 1 else 0,
            onDismiss = { choice = null },
        ) {
            core.setImperial(it == 1)
            choice = null
        }
    }
}

private enum class Choice { Mode, Units, AmbientMode }

/** Range of the per-gear limit sliders, in km/h. */
internal const val LIMIT_MIN = 1f
internal const val LIMIT_MAX = 60f

internal fun snapLimit(raw: Float): Int =
    raw.roundToInt().coerceIn(LIMIT_MIN.toInt(), LIMIT_MAX.toInt())

@Composable
private fun LimitSlider(
    shape: RoundedCornerShape,
    label: String,
    value: Int,
    enabled: Boolean,
    onCommit: (Int) -> Unit,
) {
    // Track the drag locally so the slider stays smooth, and only write on release.
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    val haptics = LocalHaptics.current
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                        .copy(alpha = if (enabled) 1f else 0.38f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = local.roundToInt().toString() + " " + stringResource(R.string.unit_kmh),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = local,
                // The detents the slider reports back land a hair either side of the whole
                // number they represent, so truncating dropped every value that arrived just
                // under: 24 appeared twice and 25 could not be selected at all. Snapping to
                // the nearest whole number keeps each step reachable.
                onValueChange = { raw ->
                    val next = snapLimit(raw).toFloat()
                    if (next != local) haptics?.perform(Haptic.Tick)
                    local = next
                },
                onValueChangeFinished = {
                    haptics?.perform(Haptic.Confirm)
                    onCommit(local.roundToInt())
                },
                valueRange = LIMIT_MIN..LIMIT_MAX,
                steps = (LIMIT_MAX - LIMIT_MIN).toInt() - 1,
                enabled = enabled,
            )
        }
    }
}

/** One of the seven colours the stock lighting page offers. */
private data class AmbientColour(val hue: Int, val labelRes: Int, val swatch: Color)

private val AmbientColours = listOf(
    AmbientColour(360, R.string.colour_red, Color(0xFFE53935)),
    AmbientColour(30, R.string.colour_orange, Color(0xFFFB8C00)),
    AmbientColour(60, R.string.colour_yellow, Color(0xFFFDD835)),
    AmbientColour(120, R.string.colour_green, Color(0xFF43A047)),
    AmbientColour(210, R.string.colour_blue, Color(0xFF039BE5)),
    AmbientColour(0, R.string.colour_white, Color(0xFFFFFFFF)),
    AmbientColour(285, R.string.colour_purple, Color(0xFF8E24AA)),
)

@Composable
private fun ambientModeLabels(): List<String> = listOf(
    stringResource(R.string.ambient_mode_off),
    stringResource(R.string.ambient_mode_cycle),
    stringResource(R.string.ambient_mode_cycle_breathe),
    stringResource(R.string.ambient_mode_solid),
    stringResource(R.string.ambient_mode_solid_breathe),
)

@Composable
private fun ColourRow(
    shape: RoundedCornerShape,
    selected: Int,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    val haptics = LocalHaptics.current
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.ambient_light_colour),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
                    .copy(alpha = if (enabled) 1f else 0.38f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmbientColours.forEach { colour ->
                    val active = colour.hue == selected
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colour.swatch.copy(alpha = if (enabled) 1f else 0.38f))
                            .border(
                                width = if (active) 3.dp else 1.dp,
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable(enabled = enabled) {
                                haptics?.perform(Haptic.Confirm)
                                onPick(colour.hue)
                            },
                    )
                }
            }
        }
    }
}
