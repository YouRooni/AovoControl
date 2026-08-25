package dev.rooni.aovo.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveEta
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.R
import dev.rooni.aovo.ble.ConnectionState
import dev.rooni.aovo.ble.ScooterFamily
import dev.rooni.aovo.ble.CoreEvent
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.component.SectionTitle
import dev.rooni.aovo.ui.component.SettingRow
import dev.rooni.aovo.ui.component.Section
import dev.rooni.aovo.ui.component.SwitchRow

@Composable
fun ModuleScreen(viewModel: AovoViewModel, modifier: Modifier = Modifier) {
    val core = viewModel.core
    val connection by core.connection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val family by core.family.collectAsStateWithLifecycle()

    // modules have. A ViCont dashboard has no such channel and no password at all, so the
    // rows are disabled rather than accepting input that goes nowhere.
    val supportsModule = family != ScooterFamily.VICONT
    val connected = connection == ConnectionState.CONNECTED && supportsModule

    var nfcEnabled by remember { mutableStateOf(false) }
    var voiceOff by remember { mutableStateOf(false) }
    var driveMode by remember { mutableStateOf(1) }
    var moduleType by remember { mutableStateOf("") }

    var renaming by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var confirmClearCards by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        if (connected) {
            core.queryNfc()
            core.queryVoice()
            core.queryDriveMode()
            core.queryDeviceType()
        }
    }

    LaunchedEffect(Unit) {
        core.events.collect { event ->
            when (event) {
                is CoreEvent.NfcState -> nfcEnabled = event.enabled
                is CoreEvent.VoiceState -> voiceOff = event.type == 1
                is CoreEvent.DriveMode -> driveMode = event.type
                is CoreEvent.DeviceType -> moduleType = event.value
                else -> Unit
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        if (!supportsModule) NoticeCard(text = stringResource(R.string.module_unsupported))
        SectionTitle(stringResource(R.string.module))
        Section {
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.device),
                    value = settings.lastDeviceName.ifBlank { "—" },
                    icon = Icons.Filled.Bluetooth,
                )
            }
            tileIf(moduleType.isNotBlank()) { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.module_type),
                    value = moduleType,
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.rename_scooter),
                    icon = Icons.Filled.Bluetooth,
                    enabled = connected,
                    onClick = { renaming = true },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.change_password),
                    icon = Icons.Filled.Password,
                    enabled = connected,
                    onClick = { changingPassword = true },
                )
            }
        }

        SectionTitle(stringResource(R.string.nfc_unlock))
        Section {
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.nfc_unlock),
                    icon = Icons.Filled.CreditCard,
                    checked = nfcEnabled,
                    enabled = connected,
                    onCheckedChange = {
                        nfcEnabled = it
                        core.setNfc(it)
                    },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.nfc_clear),
                    icon = Icons.Filled.Delete,
                    enabled = connected,
                    onClick = { confirmClearCards = true },
                )
            }
        }

        SectionTitle(stringResource(R.string.behaviour))
        Section {
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.voice_prompts),
                    icon = Icons.Filled.RecordVoiceOver,
                    checked = !voiceOff,
                    enabled = connected,
                    onCheckedChange = { on ->
                        voiceOff = !on
                        core.setVoice(if (on) 0 else 1)
                    },
                )
            }
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.dual_motor),
                    icon = Icons.Filled.DriveEta,
                    checked = driveMode == 2,
                    enabled = connected,
                    onCheckedChange = { dual ->
                        driveMode = if (dual) 2 else 1
                        core.setDriveMode(driveMode)
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.module_feature_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.rename_scooter),
            label = stringResource(R.string.new_name),
            initial = settings.lastDeviceName,
            validate = { it.isNotBlank() && it.length <= 20 },
            onDismiss = { renaming = false },
        ) {
            core.renameDevice(it)
            renaming = false
        }
    }

    if (changingPassword) {
        PasswordChangeDialog(
            currentPassword = settings.lastDevicePassword,
            onDismiss = { changingPassword = false },
            onError = viewModel::notify,
        ) {
            viewModel.changePassword(it)
            changingPassword = false
        }
    }

    if (confirmClearCards) {
        ConfirmDialog(
            title = stringResource(R.string.nfc_clear),
            message = stringResource(R.string.nfc_clear),
            onDismiss = { confirmClearCards = false },
            onConfirm = {
                core.clearNfcCards()
                confirmClearCards = false
            },
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    validate: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = validate(text), onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PasswordChangeDialog(
    currentPassword: String,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Prefill what we already use to connect: the owner rarely knows it by heart, and
    // getting the old one wrong is the fastest way to end up locked out.
    var old by remember { mutableStateOf(currentPassword) }
    var fresh by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }

    val wrongOld = stringResource(R.string.password_wrong_old)
    val mismatch = stringResource(R.string.password_mismatch)

    fun digits(value: String) = value.filter { it.isDigit() }.take(6)
    val complete = old.length == 6 && fresh.length == 6 && repeat.length == 6 && acknowledged

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_password)) },
        text = {
            Column(
                modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (currentPassword.isNotEmpty()) {
                        stringResource(R.string.password_known_hint, currentPassword)
                    } else {
                        stringResource(R.string.password_unknown_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasswordField(stringResource(R.string.current_password), old) { old = digits(it) }
                PasswordField(
                    label = stringResource(R.string.new_password),
                    value = fresh,
                    supporting = stringResource(R.string.password_rule),
                ) { fresh = digits(it) }
                PasswordField(
                    stringResource(R.string.confirm_password),
                    repeat,
                ) { repeat = digits(it) }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.change_password_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { acknowledged = !acknowledged },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text(
                        text = stringResource(R.string.password_ack),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = complete,
                onClick = {
                    when {
                        currentPassword.isNotEmpty() && old != currentPassword -> onError(wrongOld)
                        fresh != repeat -> onError(mismatch)
                        else -> onConfirm(fresh)
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    supporting: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
