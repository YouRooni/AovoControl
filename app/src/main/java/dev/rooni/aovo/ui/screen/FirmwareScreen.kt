package dev.rooni.aovo.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.R
import dev.rooni.aovo.ble.ConnectionState
import dev.rooni.aovo.ble.OtaState
import dev.rooni.aovo.ble.VicontApiClient
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.component.SectionTitle
import dev.rooni.aovo.ui.component.SettingRow
import dev.rooni.aovo.ui.component.Section
import dev.rooni.aovo.ui.component.SectionDefaults

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FirmwareScreen(viewModel: AovoViewModel, modifier: Modifier = Modifier) {
    val core = viewModel.core
    val esc by core.escInfo.collectAsStateWithLifecycle()
    val versions by core.firmwareVersions.collectAsStateWithLifecycle()
    val boards by core.versions.collectAsStateWithLifecycle()
    val ota by core.ota.collectAsStateWithLifecycle()
    val onlineUpdate by viewModel.onlineUpdate.collectAsStateWithLifecycle()
    val connection by core.connection.collectAsStateWithLifecycle()
    val connected = connection == ConnectionState.CONNECTED
    val context = LocalContext.current

    var target by remember(versions) { mutableStateOf(versions.firstOrNull().orEmpty()) }
    var pickVersion by remember { mutableStateOf(false) }
    var confirmFlash by remember { mutableStateOf<(() -> Unit)?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            viewModel.notify(context.getString(R.string.flash_failed))
        } else {
            confirmFlash = { core.flashFirmware(bytes) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        // shown. The motor controller runs its own firmware and is the half that matters for
        // anything to do with how the scooter drives.
        boards?.let { v ->
            SectionTitle(stringResource(R.string.display_board))
            Section {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.hardware_version),
                        value = v.instrumentHardware.toString(),
                        icon = Icons.Filled.Memory,
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.firmware_version),
                        value = v.instrumentSoftware.toString(),
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.board_id),
                        value = "%04X".format(v.instrumentId),
                    )
                }
            }

            SectionTitle(stringResource(R.string.motor_board))
            Section {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.hardware_version),
                        value = v.controllerHardware.toString(),
                        icon = Icons.Filled.Memory,
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.firmware_version),
                        value = v.controllerSoftware.toString(),
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.board_id),
                        value = "%04X".format(v.controllerId),
                    )
                }
            }
        }

        if (boards == null) {
            SectionTitle(stringResource(R.string.controller))
            Section {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.model),
                        value = esc.model.ifBlank { "—" },
                        icon = Icons.Filled.Memory,
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.hardware_version),
                        value = esc.hardware.ifBlank { "—" },
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.firmware_version),
                        value = esc.firmware.ifBlank { "—" },
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.bootloader_version),
                        value = esc.bootloader.ifBlank { "—" },
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.unique_code),
                        value = esc.uniqueCode.ifBlank { "—" },
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.refresh_info),
                        icon = Icons.Filled.Refresh,
                        enabled = connected,
                        onClick = core::requestEscInfo,
                    )
                }
            }
        }

        // ---- Online Cloud Firmware Section ----
        SectionTitle(stringResource(R.string.check_online_updates))
        Section {
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.target_display),
                    subtitle = if (onlineUpdate.checking) stringResource(R.string.checking_updates) else null,
                    icon = Icons.Filled.CloudSync,
                    enabled = connected && !onlineUpdate.checking && !onlineUpdate.downloading && ota.state == OtaState.IDLE,
                    onClick = { viewModel.checkOnlineUpdates("AD102030") },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.target_controller),
                    subtitle = if (onlineUpdate.checking) stringResource(R.string.checking_updates) else null,
                    icon = Icons.Filled.CloudSync,
                    enabled = connected && !onlineUpdate.checking && !onlineUpdate.downloading && ota.state == OtaState.IDLE,
                    onClick = { viewModel.checkOnlineUpdates("AE102030") },
                )
            }
        }

        // Online check result card
        onlineUpdate.result?.let { result ->
            Spacer(Modifier.height(8.dp))
            when (result) {
                is VicontApiClient.CheckResult.Available -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SectionDefaults.HorizontalPadding, vertical = 4.dp),
                        shape = RoundedCornerShape(SectionDefaults.OuterCorner),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.online_update_available, result.firmware.version.toString()),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (result.firmware.changelog.isNotBlank()) {
                                Text(
                                    text = result.firmware.changelog,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Button(
                                onClick = {
                                    confirmFlash = {
                                        viewModel.downloadAndFlashOnline(result.firmware.downloadUrl)
                                    }
                                },
                                enabled = connected && ota.state == OtaState.IDLE && !onlineUpdate.downloading,
                            ) {
                                Text(stringResource(R.string.download_and_flash))
                            }
                        }
                    }
                }
                is VicontApiClient.CheckResult.NoUpdate -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SectionDefaults.HorizontalPadding, vertical = 4.dp),
                        shape = RoundedCornerShape(SectionDefaults.OuterCorner),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Text(
                            text = stringResource(R.string.online_update_none),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                is VicontApiClient.CheckResult.NotFound -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SectionDefaults.HorizontalPadding, vertical = 4.dp),
                        shape = RoundedCornerShape(SectionDefaults.OuterCorner),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Text(
                            text = result.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                is VicontApiClient.CheckResult.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SectionDefaults.HorizontalPadding, vertical = 4.dp),
                        shape = RoundedCornerShape(SectionDefaults.OuterCorner),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Text(
                            text = result.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }

        if (onlineUpdate.downloading) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SectionDefaults.HorizontalPadding, vertical = 4.dp),
                shape = RoundedCornerShape(SectionDefaults.OuterCorner),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.downloading_firmware),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearWavyProgressIndicator(
                        progress = { 0.5f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        SectionTitle(stringResource(R.string.firmware))

        AnimatedVisibility(visible = ota.state != OtaState.IDLE) {
            OtaCard(
                percent = ota.percent,
                message = when (ota.state) {
                    OtaState.DONE -> stringResource(R.string.flash_done)
                    OtaState.FAILED -> ota.message.ifBlank { stringResource(R.string.flash_failed) }
                    else -> ota.message
                },
                failed = ota.state == OtaState.FAILED,
                done = ota.state == OtaState.DONE,
                onDismiss = core::cancelFirmware,
            )
        }

        Section {
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.available_versions),
                    value = if (versions.isEmpty()) "—" else target.ifBlank { versions.first() },
                    icon = Icons.Filled.SystemUpdate,
                    enabled = connected && versions.isNotEmpty(),
                    onClick = { pickVersion = true },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.flash),
                    subtitle = if (versions.isEmpty()) stringResource(R.string.no_firmware) else null,
                    enabled = connected && target.isNotBlank() && ota.state == OtaState.IDLE,
                    onClick = { confirmFlash = { core.flashFirmware(target) } },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                enabled = connected && ota.state == OtaState.IDLE,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.flash_from_file))
            }
        }
    }

    if (pickVersion && versions.isNotEmpty()) {
        ChoiceDialog(
            title = stringResource(R.string.available_versions),
            options = versions,
            selected = versions.indexOf(target).coerceAtLeast(0),
            onDismiss = { pickVersion = false },
        ) { index ->
            target = versions[index]
            pickVersion = false
        }
    }

    confirmFlash?.let { action ->
        ConfirmDialog(
            title = stringResource(R.string.flash),
            message = stringResource(R.string.flash_warning),
            onDismiss = { confirmFlash = null },
            onConfirm = {
                action()
                confirmFlash = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OtaCard(
    percent: Int,
    message: String,
    failed: Boolean,
    done: Boolean,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SectionDefaults.HorizontalPadding, vertical = 4.dp),
        shape = RoundedCornerShape(SectionDefaults.OuterCorner),
        colors = CardDefaults.cardColors(
            containerColor = when {
                failed -> MaterialTheme.colorScheme.errorContainer
                done -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = percent.toString() + "%",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!failed && !done) {
                LinearWavyProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (failed || done) {
                Button(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    }
}
