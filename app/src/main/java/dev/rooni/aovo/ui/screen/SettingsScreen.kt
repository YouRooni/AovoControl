package dev.rooni.aovo.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring


import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import dev.rooni.aovo.ui.theme.AppPalette
import dev.rooni.aovo.ui.theme.paletteScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Sync
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.BuildConfig
import dev.rooni.aovo.R
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.rooni.aovo.data.ThemeMode
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.component.SectionTitle
import dev.rooni.aovo.ui.component.SettingRow
import dev.rooni.aovo.ui.component.Section
import dev.rooni.aovo.ui.component.SwitchRow

@Composable
fun SettingsScreen(
    viewModel: AovoViewModel,
    onOpenRide: () -> Unit,
    onOpenController: () -> Unit,
    onOpenFirmware: () -> Unit,
    onOpenModule: () -> Unit,
    onOpenEngineering: () -> Unit,
    onOpenProfiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val logEntries by viewModel.logEntries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val linkFailed = stringResource(R.string.link_failed)
    val logSaved = stringResource(R.string.log_saved)
    val logSaveFailed = stringResource(R.string.log_save_failed)

    // user chooses where it lands, and nothing is written unless they ask for it.
    val exportLog = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(viewModel.logText().toByteArray())
            } ?: error("no stream")
        }.isSuccess
        viewModel.notify(if (written) logSaved else logSaveFailed)
    }

    var pickTheme by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }

    // back from AppCompat rather than from the settings flow.
    val languageTags = listOf("", "en", "ru")
    val languageLabels = listOf(
        stringResource(R.string.language_system),
        "English",
        "Русский",
    )
    val currentLanguage = AppCompatDelegate.getApplicationLocales()
        .toLanguageTags()
        .substringBefore('-')
        .let { tag -> languageTags.indexOf(tag).let { if (it < 0) 0 else it } }

    val themeLabels = listOf(
        stringResource(R.string.theme_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        SectionTitle(stringResource(R.string.device))
        Section {
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.ride_settings),
                    icon = Icons.AutoMirrored.Filled.DirectionsBike,
                    onClick = onOpenRide,
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.profiles),
                    icon = Icons.Filled.Bookmark,
                    onClick = onOpenProfiles,
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.controller_params),
                    icon = Icons.Filled.Memory,
                    onClick = onOpenController,
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.firmware),
                    icon = Icons.Filled.SystemUpdate,
                    onClick = onOpenFirmware,
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.module),
                    icon = Icons.Filled.Bluetooth,
                    onClick = onOpenModule,
                )
            }
            // offer until the owner has deliberately switched expert mode on.
            tileIf(settings.expertMode) { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.engineering_menu),
                    subtitle = stringResource(R.string.engineering_menu_desc),
                    icon = Icons.Filled.Engineering,
                    onClick = onOpenEngineering,
                )
            }
        }

        SectionTitle(stringResource(R.string.appearance))
        Section {
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.theme),
                    value = themeLabels[settings.theme.ordinal],
                    icon = Icons.Filled.Brightness6,
                    onClick = { pickTheme = true },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.language),
                    value = languageLabels[currentLanguage],
                    icon = Icons.Filled.Language,
                    onClick = { pickLanguage = true },
                )
            }
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.dynamic_color),
                    subtitle = stringResource(R.string.dynamic_color_desc),
                    icon = Icons.Filled.Palette,
                    checked = settings.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) },
                )
            }
            // The accent picker is meaningless while the wallpaper is choosing the colours.
            tileIf(!settings.dynamicColor) { shape ->
                PaletteRow(
                    shape = shape,
                    selected = settings.palette,
                    onSelect = { viewModel.setPalette(it) },
                )
            }
            tileIf(settings.theme != ThemeMode.LIGHT) { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.amoled),
                    subtitle = stringResource(R.string.amoled_desc),
                    icon = Icons.Filled.Contrast,
                    checked = settings.amoled,
                    onCheckedChange = { viewModel.setAmoled(it) },
                )
            }
        }

        SectionTitle(stringResource(R.string.behaviour))
        Section {
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.auto_connect),
                    subtitle = stringResource(R.string.auto_connect_desc),
                    icon = Icons.Filled.Sync,
                    checked = settings.autoConnect,
                    onCheckedChange = { viewModel.setAutoConnect(it) },
                )
            }
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.keep_screen_on),
                    subtitle = stringResource(R.string.keep_screen_on_desc),
                    icon = Icons.Filled.ScreenLockPortrait,
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) },
                )
            }
            tile { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.forget_device),
                    subtitle = settings.lastDeviceName.ifBlank { null },
                    icon = Icons.Filled.Delete,
                    enabled = settings.lastDeviceAddress.isNotEmpty(),
                    onClick = { confirmForget = true },
                )
            }
        }

        SectionTitle(stringResource(R.string.section_application))
        Section {
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.haptics),
                    subtitle = stringResource(R.string.haptics_desc),
                    icon = Icons.Filled.Vibration,
                    checked = settings.haptics,
                    onCheckedChange = { viewModel.setHaptics(it) },
                )
            }
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.expert_mode),
                    subtitle = stringResource(R.string.expert_mode_desc),
                    icon = Icons.Filled.Science,
                    checked = settings.expertMode,
                    onCheckedChange = { viewModel.setExpertMode(it) },
                )
            }
            tile { shape ->
                SwitchRow(
                    shape = shape,
                    title = stringResource(R.string.logging),
                    subtitle = stringResource(R.string.logging_desc),
                    icon = Icons.Filled.BugReport,
                    checked = settings.logging,
                    onCheckedChange = { viewModel.setLogging(it) },
                )
            }
            tileIf(settings.logging) { shape ->
                SettingRow(
                    shape = shape,
                    title = stringResource(R.string.export_log),
                    subtitle = pluralStringResource(
                        R.plurals.log_entries, logEntries.size, logEntries.size,
                    ),
                    icon = Icons.Filled.Download,
                    enabled = logEntries.isNotEmpty(),
                    onClick = { exportLog.launch(viewModel.logFileName()) },
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }

    if (pickTheme) {
        ChoiceDialog(
            title = stringResource(R.string.theme),
            options = themeLabels,
            selected = settings.theme.ordinal,
            onDismiss = { pickTheme = false },
        ) { index ->
            viewModel.setTheme(ThemeMode.entries[index])
            pickTheme = false
        }
    }

    if (pickLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = languageLabels,
            selected = currentLanguage,
            onDismiss = { pickLanguage = false },
        ) { index ->
            AppCompatDelegate.setApplicationLocales(
                if (index == 0) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(languageTags[index])
            )
            pickLanguage = false
        }
    }

    if (confirmForget) {
        ConfirmDialog(
            title = stringResource(R.string.forget_device),
            message = settings.lastDeviceName,
            onDismiss = { confirmForget = false },
            onConfirm = {
                viewModel.forgetDevice()
                confirmForget = false
            },
        )
    }
}

private const val AUTHOR_HANDLE = "@YouRooni"
private const val AUTHOR_URL = "https://YouRooni.t.me"
private const val DONATE_URL = "https://payRooni.t.me"

/** Author of the ViCont protocol notes this app's scooter compatibility is built on. */
private const val CONTRIBUTOR_HANDLE = "vova7878"
private const val CONTRIBUTOR_URL = "https://4pda.to/forum/index.php?showuser=6332385"

private fun openLink(
    context: Context,
    url: String,
    failureMessage: String,
    viewModel: AovoViewModel,
) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) viewModel.notify(failureMessage)
}

@Composable
private fun PaletteRow(
    shape: RoundedCornerShape,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.accent_colour),
                style = MaterialTheme.typography.bodyLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppPalette.entries.forEach { palette ->
                    val swatch = paletteScheme(palette, dark, amoled = false).primary
                    val active = palette.key == selected
                    val corner by animateDpAsState(
                        targetValue = if (active) 12.dp else 22.dp,
                        label = "swatch",
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(corner))
                            .background(swatch)
                            .clickable { onSelect(palette.key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
