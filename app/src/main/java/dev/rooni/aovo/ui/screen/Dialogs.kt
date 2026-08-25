package dev.rooni.aovo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rooni.aovo.R
import dev.rooni.aovo.ui.Haptic
import dev.rooni.aovo.ui.LocalHaptics
import kotlin.math.roundToInt

/** Single-choice picker used everywhere an enumerated register is edited. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val haptics = LocalHaptics.current
    val pick: (Int) -> Unit = { index ->
        haptics?.perform(Haptic.Press)
        onSelect(index)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pick(index) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = index == selected, onClick = { pick(index) })
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

/** Numeric picker for a continuous register, with the unit shown next to the value. */
@Composable
fun ValueDialog(
    title: String,
    initial: Float,
    min: Float,
    max: Float,
    step: Float,
    unit: String,
    decimals: Int,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var value by remember { mutableFloatStateOf(initial.coerceIn(min, max)) }
    val steps = if (step <= 0f) 0 else (((max - min) / step).roundToInt() - 1).coerceAtLeast(0)
    val haptics = LocalHaptics.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = formatValue(value, decimals) + (if (unit.isEmpty()) "" else " $unit"),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = value,
                    onValueChange = { raw ->
                        val next = if (step <= 0f) raw else {
                            (min + ((raw - min) / step).roundToInt() * step).coerceIn(min, max)
                        }
                        if (next != value) haptics?.perform(Haptic.Tick)
                        value = next
                    },
                    valueRange = min..max,
                    steps = steps,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatValue(min, decimals),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatValue(max, decimals),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptics?.perform(Haptic.Confirm)
                onConfirm(value)
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.ok),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val haptics = LocalHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                haptics?.perform(Haptic.Confirm)
                onConfirm()
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

fun formatValue(value: Float, decimals: Int): String =
    if (decimals <= 0) value.roundToInt().toString()
    else String.format(java.util.Locale.US, "%." + decimals + "f", value)
