package dev.rooni.aovo.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.R
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.component.MetricCard
import dev.rooni.aovo.ui.component.Section
import dev.rooni.aovo.ui.component.SectionGrid
import dev.rooni.aovo.ui.component.SectionTitle
import dev.rooni.aovo.ui.component.SettingRow

@Composable
fun DataScreen(viewModel: AovoViewModel, modifier: Modifier = Modifier) {
    val telemetry by viewModel.core.telemetry.collectAsStateWithLifecycle()
    val ride by viewModel.core.ride.collectAsStateWithLifecycle()
    val esc by viewModel.core.escInfo.collectAsStateWithLifecycle()
    val calibration by viewModel.core.calibration.collectAsStateWithLifecycle()
    val sensors by viewModel.core.sensors.collectAsStateWithLifecycle()

    val distanceUnit = stringResource(if (ride.imperial) R.string.unit_mi else R.string.unit_km)

    Column(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        SectionTitle(stringResource(R.string.live_data))
        SectionGrid {
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.battery),
                    value = telemetry.battery.toString(),
                    unit = stringResource(R.string.unit_percent),
                    icon = Icons.Filled.BatteryFull,
                    accent = if (telemetry.battery <= 15) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.voltage),
                    value = format1(telemetry.voltage),
                    unit = stringResource(R.string.unit_v),
                    icon = Icons.Filled.ElectricBolt,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.current),
                    value = format1(telemetry.current),
                    unit = stringResource(R.string.unit_a),
                    icon = Icons.Filled.Bolt,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.power),
                    value = format1(telemetry.power),
                    unit = stringResource(R.string.unit_w),
                    icon = Icons.Filled.Power,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.controller_temp),
                    value = telemetry.escTemperature.toString(),
                    unit = stringResource(R.string.unit_c),
                    icon = Icons.Filled.Thermostat,
                    accent = if (telemetry.escTemperature >= 70) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.motor_temp),
                    value = telemetry.motorTemperature.toString(),
                    unit = stringResource(R.string.unit_c),
                    icon = Icons.Filled.DeviceThermostat,
                    accent = if (telemetry.motorTemperature >= 90) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.trip),
                    value = format1(telemetry.tripDistance),
                    unit = distanceUnit,
                    icon = Icons.Filled.Route,
                )
            }
            tile { shape ->
                MetricCard(
                    shape = shape,
                    label = stringResource(R.string.odometer),
                    value = format1(telemetry.totalDistance),
                    unit = distanceUnit,
                    icon = Icons.Filled.Timeline,
                )
            }
        }

        SectionTitle(stringResource(R.string.controller))
        Section {
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.status_register),
                    value = telemetry.faultCode,
                    icon = Icons.Filled.Memory,
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.km_to_service),
                    value = remaining(
                        ride.serviceMileage,
                        ride.lastServiceMileage,
                        telemetry.totalDistance,
                    ) + " " + distanceUnit,
                    icon = Icons.Filled.Build,
                )
            }
            tileIf(ride.displayVersion.isNotBlank()) { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.module_type),
                    value = ride.displayVersion,
                )
            }
        }

        sensors?.let { s ->
            SectionTitle(stringResource(R.string.controls))
            Section {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.throttle),
                        subtitle = stringResource(R.string.sensor_raw, s.throttleRaw),
                        value = s.throttle.toString(),
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.brake),
                        subtitle = stringResource(R.string.sensor_raw, s.brake1Raw),
                        value = s.brake1.toString(),
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.brake_second),
                        // same as a lever that is simply not being pulled.
                        subtitle = if (s.hasSecondBrake) {
                            stringResource(R.string.sensor_raw, s.brake2Raw)
                        } else {
                            stringResource(R.string.sensor_absent)
                        },
                        value = if (s.hasSecondBrake) s.brake2.toString() else "—",
                    )
                }
            }
        }

        // is ever written wrong these are the only numbers that can put the scooter back.
        calibration?.let { c ->
            SectionTitle(stringResource(R.string.motor_calibration))
            Section {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.calibration_phase_order),
                        subtitle = stringResource(R.string.calibration_phase_order_desc),
                        value = c.phaseOrder.toString(),
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.calibration_coefficients),
                        subtitle = stringResource(R.string.calibration_coefficients_desc),
                        value = "${c.coefficient1} · ${c.coefficient2} · ${c.coefficient3}",
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.calibration_writes),
                        subtitle = stringResource(R.string.calibration_writes_desc),
                        value = "${c.displayWrites} / ${c.motorWrites}",
                    )
                }
            }
        }
    }
}

private fun remaining(interval: Int, lastService: Int, total: Float): String {
    val next = lastService + interval
    val left = next - total
    return if (left <= 0) "0" else left.toInt().toString()
}
