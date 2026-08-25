package dev.rooni.aovo.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.R
import kotlin.math.roundToInt
import dev.rooni.aovo.ble.ConnectionState
import dev.rooni.aovo.ble.ScooterFamily
import dev.rooni.aovo.ble.ParamRegistry
import dev.rooni.aovo.ble.Telemetry
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.component.SectionTitle
import dev.rooni.aovo.ui.component.SettingRow
import dev.rooni.aovo.ui.component.Section
import dev.rooni.aovo.ui.component.SectionDefaults
import dev.rooni.aovo.ui.component.SwitchRow

private enum class Editor {
    ThrottleResponse, BrakeResponse, SpeedLimit,
    DischargeCurrent, BrakingCurrent, VoltageProtection,
    MotorDiameter, PolePairs, Modulation, Pwm,
    CruiseDelay, AutoShutdown, ServiceInterval, LastService,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ControllerScreen(viewModel: AovoViewModel, modifier: Modifier = Modifier) {
    val core = viewModel.core
    val params by core.params.collectAsStateWithLifecycle()
    val connection by core.connection.collectAsStateWithLifecycle()
    val telemetry by core.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val family by core.family.collectAsStateWithLifecycle()
    val connected = connection == ConnectionState.CONNECTED

    // it, so the row is shown greyed rather than silently doing nothing when tapped.
    val supportsCruiseDelay = family != ScooterFamily.VICONT

    // cannot take effect would just produce a change the scooter silently ignores.
    val movingLock = connected && telemetry.settingsLocked
    val movingHint = stringResource(
        R.string.settings_locked_moving,
        Telemetry.SETTINGS_LOCK_SPEED_KMH.toInt(),
    )

    fun shown(value: String) = if (connected) value else "—"

    var editor by remember { mutableStateOf<Editor?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    /** Opens [target] unless the scooter is rolling, in which case it says why not. */
    fun openEditor(target: Editor) {
        if (movingLock) viewModel.notify(movingHint) else editor = target
    }

    LaunchedEffect(connected) {
        if (connected) core.queryAdvParams()
    }

    // Rolling away with an editor open would let a value be committed that never lands.
    LaunchedEffect(movingLock) {
        if (movingLock && editor != null) {
            editor = null
            viewModel.notify(movingHint)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        SectionTitle(stringResource(R.string.controller_params))
        if (movingLock) MovingLockWarning()
        Section {
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.throttle_response),
                    value = shown(params.throttleResponse.toString()),
                    icon = Icons.Filled.Tune,
                    enabled = connected,
                    onClick = { openEditor(Editor.ThrottleResponse) },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.brake_response),
                    value = shown(params.brakeResponse.toString()),
                    icon = Icons.Filled.Tune,
                    enabled = connected,
                    onClick = { openEditor(Editor.BrakeResponse) },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.global_speed_limit),
                    subtitle = stringResource(R.string.global_speed_limit_desc),
                    value = shown(params.speedLimit.toString() + " " + stringResource(R.string.unit_kmh)),
                    icon = Icons.Filled.Speed,
                    enabled = connected,
                    onClick = { openEditor(Editor.SpeedLimit) },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.cruise_activation),
                    value = if (supportsCruiseDelay) shown(
                        params.cruiseActivationTime.toString() +
                            " " + stringResource(R.string.unit_seconds)
                    ) else stringResource(R.string.not_supported),
                    icon = Icons.Filled.Timer,
                    enabled = connected && supportsCruiseDelay,
                    onClick = { openEditor(Editor.CruiseDelay) },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.auto_shutdown),
                    value = shown(
                        params.autoShutdownTime.toString() +
                            " " + stringResource(R.string.unit_minutes)
                    ),
                    icon = Icons.Filled.PowerSettingsNew,
                    enabled = connected,
                    onClick = { openEditor(Editor.AutoShutdown) },
                )
            }
        }

        SectionTitle(stringResource(R.string.service))
        Section {
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.service_interval),
                    value = shown(params.serviceMileage.toString() + " " + stringResource(R.string.unit_km)),
                    enabled = connected,
                    onClick = { openEditor(Editor.ServiceInterval) },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.last_service),
                    value = shown(
                        params.lastServiceMileage.toString() +
                            " " + stringResource(R.string.unit_km)
                    ),
                    enabled = connected,
                    onClick = { openEditor(Editor.LastService) },
                )
            }
        }

        if (settings.expertMode) {
            SectionTitle(stringResource(R.string.expert_params))
            ExpertWarning()
            // Everything in this section except the discharge current is a ZYD controller
            // register. ViCont has no register page behind it, so those rows would show a
            // default that has nothing to do with the connected scooter — and the settings
            // that do exist there live in the engineering menu instead.
            val registersAvailable = family != ScooterFamily.VICONT
            if (!registersAvailable) {
                NoticeCard(text = stringResource(R.string.expert_params_vicont))
            }
            Section {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.max_discharge_current),
                        value = shown(
                                formatValue(
                                    params.maxDischargeCurrent,
                                    if (family == ScooterFamily.VICONT) 0 else 1,
                                ) + " " + stringResource(R.string.unit_a)
                            ),
                        icon = Icons.Filled.ElectricBolt,
                        enabled = connected,
                        onClick = { openEditor(Editor.DischargeCurrent) },
                    )
                }
                tileIf(registersAvailable) { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.max_braking_current),
                        value = shown(
                                formatValue(params.maxBrakingCurrent, 1) + " " + stringResource(R.string.unit_a)
                            ),
                        icon = Icons.Filled.ElectricBolt,
                        enabled = connected,
                        onClick = { openEditor(Editor.BrakingCurrent) },
                    )
                }
                tileIf(registersAvailable) { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.voltage_protection),
                        value = shown(
                                formatValue(params.voltageProtection, 1) + " " + stringResource(R.string.unit_v)
                            ),
                        enabled = connected,
                        onClick = { openEditor(Editor.VoltageProtection) },
                    )
                }
                tileIf(registersAvailable) { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.motor_diameter),
                        value = shown(
                                formatValue(params.motorDiameter, 1) + " " + stringResource(R.string.unit_inch)
                            ),
                        enabled = connected,
                        onClick = { openEditor(Editor.MotorDiameter) },
                    )
                }
                tileIf(registersAvailable) { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.motor_pole_pairs),
                        value = shown(params.motorPolePairs.toString()),
                        icon = Icons.Filled.Memory,
                        enabled = connected,
                        onClick = { openEditor(Editor.PolePairs) },
                    )
                }
                tileIf(registersAvailable) { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.modulation_depth),
                        value = shown(params.maxModulationDepth.toString()),
                        enabled = connected,
                        onClick = { openEditor(Editor.Modulation) },
                    )
                }
                tileIf(registersAvailable) { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.pwm_frequency),
                        value = if (connected) ParamRegistry.pwmOptions.getOrElse(params.pwmFrequency) { "—" } else "—",
                        enabled = connected,
                        onClick = { openEditor(Editor.Pwm) },
                    )
                }
                tileIf(registersAvailable) { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.restore_defaults),
                        icon = Icons.Filled.RestartAlt,
                        enabled = connected,
                        onClick = { if (movingLock) viewModel.notify(movingHint) else confirmReset = true },
                    )
                }
            }
        }
    }

    EditorDialog(editor, viewModel) { editor = null }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.restore_defaults),
            message = stringResource(R.string.restore_defaults_warning),
            onDismiss = { confirmReset = false },
            onConfirm = {
                core.restoreControllerDefaults()
                confirmReset = false
            },
        )
    }
}

@Composable
fun MovingLockWarning(modifier: Modifier = Modifier) {
    NoticeCard(
        text = stringResource(
            R.string.settings_locked_banner,
            Telemetry.SETTINGS_LOCK_SPEED_KMH.toInt(),
        ),
        modifier = modifier,
    )
}

@Composable
private fun ExpertWarning() {
    NoticeCard(text = stringResource(R.string.expert_warning))
}

/** Error-toned card used for the warnings that sit above a section. */
@Composable
fun NoticeCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SectionDefaults.HorizontalPadding, vertical = 4.dp),
        shape = RoundedCornerShape(SectionDefaults.OuterCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun EditorDialog(editor: Editor?, viewModel: AovoViewModel, onDismiss: () -> Unit) {
    val core = viewModel.core
    val params by core.params.collectAsStateWithLifecycle()

    when (editor) {
        null -> Unit

        // Where the scooter reports a ceiling, the dialog stops there: it silently clamps
        // anything above, so offering more only produces settings that do not stick.
        Editor.ThrottleResponse -> {
            val ceiling = params.throttleResponseCeiling ?: 10
            ValueDialog(
                title = stringResource(R.string.throttle_response),
                initial = params.throttleResponse.toFloat(),
                min = 1f, max = ceiling.toFloat(), step = 1f, unit = "", decimals = 0,
                onDismiss = onDismiss,
            ) { core.setThrottleResponse(it.roundToInt().coerceIn(1, ceiling)); onDismiss() }
        }

        Editor.BrakeResponse -> {
            val ceiling = params.brakeResponseCeiling ?: 10
            ValueDialog(
                title = stringResource(R.string.brake_response),
                initial = params.brakeResponse.toFloat(),
                min = 1f, max = ceiling.toFloat(), step = 1f, unit = "", decimals = 0,
                onDismiss = onDismiss,
            ) { core.setBrakeResponse(it.roundToInt().coerceIn(1, ceiling)); onDismiss() }
        }

        Editor.SpeedLimit -> {
            val ceiling = params.speedLimitCeiling ?: 60
            ValueDialog(
                title = stringResource(R.string.speed_limit),
                initial = params.speedLimit.toFloat(),
                min = 2f, max = ceiling.toFloat(), step = 1f,
                unit = stringResource(R.string.unit_kmh), decimals = 0,
                onDismiss = onDismiss,
            ) { core.setSpeedLimit(it.roundToInt().coerceIn(2, ceiling)); onDismiss() }
        }

        // ViCont has no such setting; the row is disabled there rather than writing nothing.
        Editor.CruiseDelay -> ValueDialog(
            title = stringResource(R.string.cruise_activation),
            initial = params.cruiseActivationTime.toFloat(),
            min = 2f, max = 30f, step = 1f,
            unit = stringResource(R.string.unit_seconds), decimals = 0,
            onDismiss = onDismiss,
        ) { core.setCruiseActivationTime(it.roundToInt()); onDismiss() }

        // ViCont keeps this as engineering parameter P5, whose range runs to 99 and whose
        // factory value is 60 — a dialog stopping at 30 could not even restore the default.
        Editor.AutoShutdown -> {
            val viCont = core.family.value == ScooterFamily.VICONT
            ValueDialog(
                title = stringResource(R.string.auto_shutdown),
                initial = params.autoShutdownTime.toFloat(),
                min = if (viCont) 1f else 2f,
                max = if (viCont) 99f else 30f,
                step = 1f,
                unit = stringResource(R.string.unit_minutes), decimals = 0,
                onDismiss = onDismiss,
            ) { core.setAutoShutdownTime(it.roundToInt()); onDismiss() }
        }

        // ViCont stores this as whole amps. Offering half-amp detents made every other
        // position round to its neighbour, so the dialog showed a value it had not set.
        Editor.DischargeCurrent -> {
            val viCont = core.family.value == ScooterFamily.VICONT
            val ceiling = params.dischargeCurrentCeiling?.toFloat() ?: if (viCont) 25f else 20f
            ValueDialog(
                title = stringResource(R.string.max_discharge_current),
                initial = params.maxDischargeCurrent,
                min = if (viCont) 1f else 0.5f,
                max = ceiling,
                step = if (viCont) 1f else 0.5f,
                unit = stringResource(R.string.unit_a),
                decimals = if (viCont) 0 else 1,
                onDismiss = onDismiss,
            ) { core.setMaxDischargeCurrent(it.coerceAtMost(ceiling)); onDismiss() }
        }

        Editor.BrakingCurrent -> ValueDialog(
            title = stringResource(R.string.max_braking_current),
            initial = params.maxBrakingCurrent,
            min = 0.5f, max = 30f, step = 0.5f,
            unit = stringResource(R.string.unit_a), decimals = 1,
            onDismiss = onDismiss,
        ) { core.setMaxBrakingCurrent(it); onDismiss() }

        Editor.VoltageProtection -> ValueDialog(
            title = stringResource(R.string.voltage_protection),
            initial = params.voltageProtection,
            min = 18f, max = 44f, step = 0.5f,
            unit = stringResource(R.string.unit_v), decimals = 1,
            onDismiss = onDismiss,
        ) { core.setVoltageProtection(it); onDismiss() }

        Editor.MotorDiameter -> ValueDialog(
            title = stringResource(R.string.motor_diameter),
            initial = params.motorDiameter,
            min = 0.5f, max = 15f, step = 0.5f,
            unit = stringResource(R.string.unit_inch), decimals = 1,
            onDismiss = onDismiss,
        ) { core.setMotorDiameter(it); onDismiss() }

        Editor.PolePairs -> ValueDialog(
            title = stringResource(R.string.motor_pole_pairs),
            initial = params.motorPolePairs.toFloat(),
            min = 1f, max = 30f, step = 1f, unit = "", decimals = 0,
            onDismiss = onDismiss,
        ) { core.setMotorPolePairs(it.roundToInt()); onDismiss() }

        Editor.Modulation -> ValueDialog(
            title = stringResource(R.string.modulation_depth),
            initial = params.maxModulationDepth.toFloat(),
            min = 1f, max = 50f, step = 1f, unit = "", decimals = 0,
            onDismiss = onDismiss,
        ) { core.setMaxModulationDepth(it.roundToInt()); onDismiss() }

        Editor.Pwm -> ChoiceDialog(
            title = stringResource(R.string.pwm_frequency),
            options = ParamRegistry.pwmOptions,
            selected = params.pwmFrequency,
            onDismiss = onDismiss,
        ) { core.setPwmFrequency(it); onDismiss() }

        Editor.ServiceInterval -> ValueDialog(
            title = stringResource(R.string.service_interval),
            initial = params.serviceMileage.toFloat().coerceAtLeast(50f),
            min = 50f, max = 5000f, step = 50f,
            unit = stringResource(R.string.unit_km), decimals = 0,
            onDismiss = onDismiss,
        ) { core.setServiceMileage(it.roundToInt()); onDismiss() }

        Editor.LastService -> ValueDialog(
            title = stringResource(R.string.last_service),
            initial = params.lastServiceMileage.toFloat(),
            min = 0f, max = 60000f, step = 10f,
            unit = stringResource(R.string.unit_km), decimals = 0,
            onDismiss = onDismiss,
        ) { core.setLastServiceMileage(it.roundToInt()); onDismiss() }
    }
}
