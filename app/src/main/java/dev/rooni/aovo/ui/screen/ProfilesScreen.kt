package dev.rooni.aovo.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import dev.rooni.aovo.data.ProfileIcons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.R
import dev.rooni.aovo.ble.ConnectionState
import dev.rooni.aovo.data.ProfileCapture
import dev.rooni.aovo.data.RideProfile
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.Haptic
import dev.rooni.aovo.ui.LocalHaptics
import dev.rooni.aovo.ui.component.Section
import dev.rooni.aovo.ui.component.SectionTitle

@Composable
fun ProfilesScreen(viewModel: AovoViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connection by viewModel.core.connection.collectAsStateWithLifecycle()
    val params by viewModel.core.params.collectAsStateWithLifecycle()
    val connected = connection == ConnectionState.CONNECTED

    var saving by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RideProfile?>(null) }
    var overwriting by remember { mutableStateOf<RideProfile?>(null) }
    var deleting by remember { mutableStateOf<RideProfile?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        if (settings.profiles.isEmpty()) {
            EmptyProfiles()
        } else {
            SectionTitle(stringResource(R.string.profiles))
            Text(
                text = stringResource(R.string.profiles_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
            )
            Section {
                settings.profiles.forEach { profile ->
                    tile { shape ->
                        ProfileCard(
                            profile = profile,
                            shape = shape,
                            enabled = connected,
                            onApply = { viewModel.applyProfile(profile) },
                            onOverwrite = { overwriting = profile },
                            onRename = { renaming = profile },
                            onDelete = { deleting = profile },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        FilledTonalButton(
            onClick = { saving = true },
            enabled = connected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp),
        ) {
            Icon(Icons.Filled.BookmarkAdd, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.save_profile))
        }

        if (!connected) {
            Text(
                text = stringResource(R.string.disconnected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    if (saving) {
        CaptureDialog(
            title = stringResource(R.string.save_profile),
            initialName = "",
            initialIcon = ProfileIcons.DEFAULT,
            askName = true,
            registersAvailable = params.loaded,
            onDismiss = { saving = false },
        ) { name, icon, capture ->
            viewModel.saveProfile(name, icon, capture)
            saving = false
        }
    }

    overwriting?.let { profile ->
        CaptureDialog(
            title = stringResource(R.string.overwrite),
            initialName = profile.name,
            initialIcon = profile.icon,
            askName = false,
            registersAvailable = params.loaded,
            onDismiss = { overwriting = null },
        ) { _, _, capture ->
            viewModel.overwriteProfile(profile.id, capture)
            overwriting = null
        }
    }

    renaming?.let { profile ->
        NameDialog(
            title = stringResource(R.string.edit_profile),
            initial = profile.name,
            initialIcon = profile.icon,
            onDismiss = { renaming = null },
        ) { name, icon ->
            viewModel.editProfile(profile.id, name, icon)
            renaming = null
        }
    }

    deleting?.let { profile ->
        ConfirmDialog(
            title = profile.name,
            message = stringResource(R.string.delete_profile_confirm),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.deleteProfile(profile.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: RideProfile,
    shape: RoundedCornerShape,
    enabled: Boolean,
    onApply: () -> Unit,
    onOverwrite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHaptics.current
    var menuOpen by remember { mutableStateOf(false) }
    val alpha = if (enabled) 1f else 0.38f

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) {
                    haptics?.perform(Haptic.Confirm)
                    onApply()
                }
                .padding(start = 18.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = profileIcon(profile.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ScopeTags(profile, alpha)
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.profile_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.apply_profile)) },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        enabled = enabled,
                        onClick = {
                            menuOpen = false
                            onApply()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.overwrite)) },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        enabled = enabled,
                        onClick = {
                            menuOpen = false
                            onOverwrite()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_profile)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Small pills naming the groups a profile carries, in place of a sentence of prose. */
@Composable
private fun ScopeTags(profile: RideProfile, alpha: Float) {
    val tags = buildList {
        if (profile.gear != null || profile.headLight != null) {
            add(stringResource(R.string.tag_ride))
        }
        if (profile.limitEco != null) add(stringResource(R.string.tag_mode_limits))
        if (profile.throttleResponse != null) add(stringResource(R.string.tag_throttle))
        if (profile.brakeResponse != null) add(stringResource(R.string.tag_brake))
        if (profile.speedLimit != null) add(stringResource(R.string.tag_speed_limit))
    }
    if (tags.isEmpty()) {
        Text(
            text = stringResource(R.string.profile_empty_scope),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha),
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyProfiles() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.no_profiles),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CaptureDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    askName: Boolean,
    registersAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, ProfileCapture) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    var capture by remember {
        mutableStateOf(
            ProfileCapture(
                throttleResponse = registersAvailable,
                brakeResponse = registersAvailable,
                speedLimit = registersAvailable,
            )
        )
    }
    val nameOk = !askName || name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (askName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        label = { Text(stringResource(R.string.profile_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    IconPicker(selected = icon, onSelect = { icon = it })
                }

                CaptureCheck(
                    label = stringResource(R.string.capture_ride),
                    description = stringResource(R.string.capture_ride_desc),
                    checked = capture.ride,
                    enabled = true,
                ) { capture = capture.copy(ride = it) }

                CaptureCheck(
                    label = stringResource(R.string.capture_mode_limits),
                    description = stringResource(R.string.capture_mode_limits_desc),
                    checked = capture.modeLimits,
                    enabled = true,
                ) { capture = capture.copy(modeLimits = it) }

                CaptureCheck(
                    label = stringResource(R.string.capture_throttle),
                    checked = capture.throttleResponse && registersAvailable,
                    enabled = registersAvailable,
                ) { capture = capture.copy(throttleResponse = it) }

                CaptureCheck(
                    label = stringResource(R.string.capture_brake),
                    checked = capture.brakeResponse && registersAvailable,
                    enabled = registersAvailable,
                ) { capture = capture.copy(brakeResponse = it) }

                CaptureCheck(
                    label = stringResource(R.string.capture_speed_limit_item),
                    checked = capture.speedLimit && registersAvailable,
                    enabled = registersAvailable,
                ) { capture = capture.copy(speedLimit = it) }

                if (!registersAvailable) {
                    Text(
                        text = stringResource(R.string.capture_registers_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            val effective = if (registersAvailable) {
                capture
            } else {
                capture.copy(
                    throttleResponse = false,
                    brakeResponse = false,
                    speedLimit = false,
                )
            }
            TextButton(
                enabled = effective.any && nameOk,
                onClick = { onConfirm(name, icon, effective) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** A wrapping grid of the icons a profile may wear. */
@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.profile_icon),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProfileIcons.ALL.forEach { key ->
                val active = key == selected
                Surface(
                    shape = RoundedCornerShape(if (active) 12.dp else 20.dp),
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(
                        modifier = Modifier.clickable { onSelect(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = profileIcon(key),
                            contentDescription = null,
                            tint = if (active) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureCheck(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    description: String? = null,
    onChange: (Boolean) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    initialIcon: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var icon by remember { mutableStateOf(initialIcon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                IconPicker(selected = icon, onSelect = { icon = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name, icon) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
