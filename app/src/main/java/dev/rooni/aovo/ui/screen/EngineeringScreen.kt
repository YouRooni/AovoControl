package dev.rooni.aovo.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import dev.rooni.aovo.ble.EngineeringParam
import dev.rooni.aovo.ble.EngineeringParams
import dev.rooni.aovo.ble.ParamRisk
import dev.rooni.aovo.ble.ScooterFamily
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.component.Section
import dev.rooni.aovo.ui.component.SectionTitle
import dev.rooni.aovo.ui.component.SettingRow

@Composable
fun EngineeringScreen(viewModel: AovoViewModel, modifier: Modifier = Modifier) {
    val core = viewModel.core
    val connection by core.connection.collectAsStateWithLifecycle()
    val family by core.family.collectAsStateWithLifecycle()
    val values by core.engineeringValues.collectAsStateWithLifecycle()
    val supported = family == ScooterFamily.VICONT
    val connected = connection == ConnectionState.CONNECTED && supported

    var editing by remember { mutableStateOf<EngineeringParam?>(null) }
    var warningFor by remember { mutableStateOf<EngineeringParam?>(null) }

    val shownOnDisplay = stringResource(R.string.param_shown_on_display)

    Column(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        SectionTitle(stringResource(R.string.engineering_menu))
        NoticeCard(
            text = stringResource(
                if (supported) R.string.engineering_warning else R.string.engineering_unsupported
            )
        )

        Section {
            for (param in EngineeringParams.ALL) {
                tile { shape ->
                    val stored = values[param.number]
                    SettingRow(
                        shape = shape,
                        title = "P${param.number} · " + stringResource(param.titleRes),
                        subtitle = stringResource(param.descriptionRes),
                        value = stored?.toString() ?: stringResource(R.string.param_unknown),
                        icon = param.risk.icon(),
                        enabled = connected,
                        onClick = {
                            if (param.risk == ParamRisk.NONE) editing = param else warningFor = param
                        },
                        trailing = {
                            // own screen; there is no way to get it back over the air.
                            IconButton(
                                enabled = connected,
                                onClick = {
                                    if (core.showEngineeringParam(param.number)) {
                                        viewModel.notify(shownOnDisplay)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.param_show),
                                )
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }

    warningFor?.let { param ->
        ConfirmDialog(
            title = "P${param.number} · " + stringResource(param.titleRes),
            message = stringResource(param.risk.warningRes()) + "\n\n" +
                stringResource(param.descriptionRes),
            confirmLabel = stringResource(R.string.param_edit_anyway),
            onDismiss = { warningFor = null },
            onConfirm = {
                editing = param
                warningFor = null
            },
        )
    }

    editing?.let { param ->
        val current = values[param.number] ?: param.default
        ValueDialog(
            title = "P${param.number} · " + stringResource(param.titleRes),
            initial = current.toFloat(),
            min = param.min.toFloat(),
            max = param.max.toFloat(),
            step = 1f,
            unit = "",
            decimals = 0,
            onDismiss = { editing = null },
        ) { chosen ->
            core.writeEngineeringParam(param.number, chosen.roundToInt())
            editing = null
        }
    }
}

@Composable
private fun ParamRisk.icon() = when (this) {
    ParamRisk.NONE -> Icons.Filled.Tune
    ParamRisk.CAUTION -> Icons.Filled.Science
    ParamRisk.DANGER -> Icons.Filled.Warning
    ParamRisk.UNKNOWN -> Icons.Filled.HelpOutline
}

private fun ParamRisk.warningRes(): Int = when (this) {
    ParamRisk.DANGER -> R.string.param_risk_danger
    ParamRisk.UNKNOWN -> R.string.param_risk_unknown
    else -> R.string.param_risk_caution
}

@Composable
fun ManualCommandDialog(viewModel: AovoViewModel, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    val invalid = stringResource(R.string.manual_command_invalid)
    val sent = stringResource(R.string.manual_command_sent)

    val command = parseHexByte(code)
    val bytes = parseHexBytes(payload)
    val ready = command != null && bytes != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_command)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.manual_command_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim() },
                    label = { Text(stringResource(R.string.manual_command_code)) },
                    isError = code.isNotEmpty() && command == null,
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text(stringResource(R.string.manual_command_payload)) },
                    isError = bytes == null,
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (bytes == null || (code.isNotEmpty() && command == null)) invalid
                    else stringResource(R.string.manual_command_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    if (command != null && bytes != null &&
                        viewModel.core.sendRawCommand(command, bytes)
                    ) {
                        viewModel.notify(sent)
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Parses one hex byte, with or without an `0x` prefix. Null when it is not one. */
internal fun parseHexByte(text: String): Int? =
    text.removePrefix("0x").removePrefix("0X").trim()
        .takeIf { it.isNotEmpty() && it.length <= 2 && it.all { c -> c.isHexDigit() } }
        ?.toInt(16)

/** Parses a run of hex bytes, spaces optional. Empty is valid; malformed is not. */
internal fun parseHexBytes(text: String): ByteArray? {
    val cleaned = text.replace(" ", "").replace(",", "").removePrefix("0x").removePrefix("0X")
    if (cleaned.isEmpty()) return ByteArray(0)
    if (cleaned.length % 2 != 0 || !cleaned.all { it.isHexDigit() }) return null
    return ByteArray(cleaned.length / 2) {
        cleaned.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
