package dev.rooni.aovo.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rooni.aovo.ui.Haptic
import dev.rooni.aovo.ui.LocalHaptics

object TileMetrics {
        val MinHeight = 120.dp
    val Padding = 16.dp

    /** Corner radius of an action tile that is switched off: soft, almost a pebble. */
    val RestingCorner = 30.dp

    /** Corner radius once it is switched on: squarer, so the state reads at a glance. */
    val ActiveCorner = 16.dp
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun actionTileShape(selected: Boolean): RoundedCornerShape {
    val corner by animateDpAsState(
        targetValue = if (selected) TileMetrics.ActiveCorner else TileMetrics.RestingCorner,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "actionTileCorner",
    )
    return RoundedCornerShape(corner)
}

/** Grouped-list section header, sitting just above its block of tiles. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
fun SettingRow(
    shape: RoundedCornerShape,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    /** Set to null when the caller plays its own, more specific effect. */
    clickHaptic: Haptic? = Haptic.Press,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    val haptics = LocalHaptics.current
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) Modifier.clickable {
                        clickHaptic?.let { haptics?.perform(it) }
                        onClick()
                    } else Modifier
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    )
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun SwitchRow(
    shape: RoundedCornerShape,
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHaptics.current
    val toggle: (Boolean) -> Unit = { next ->
        haptics?.perform(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
        onCheckedChange(next)
    }
    SettingRow(
        shape = shape,
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        onClick = { toggle(!checked) },
        // The toggle itself plays the on/off effect, so the row must not add a press.
        clickHaptic = null,
        trailing = {
            Switch(checked = checked, onCheckedChange = toggle, enabled = enabled)
        },
    )
}

/** Square-ish tile for the dashboard quick actions. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionTile(
    shape: RoundedCornerShape,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /** False while the dashboard is being edited: the tile still looks live but ignores taps. */
    interactive: Boolean = true,
    /** At the narrowest width there is no room for a caption, so only the icon shows. */
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tileContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tileContent",
    )
    val press by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "tileScale",
    )
    val haptics = LocalHaptics.current

    Surface(
        shape = if (interactive) actionTileShape(selected) else shape,
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TileMetrics.MinHeight)
            .scale(press),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (interactive) {
                        Modifier.clickable(enabled = enabled) {
                            haptics?.perform(if (selected) Haptic.ToggleOff else Haptic.ToggleOn)
                            onClick()
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(TileMetrics.Padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = content,
                    modifier = Modifier.size(26.dp),
                )
                if (showLabel) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Compact metric readout used in the telemetry grids. */
@Composable
fun MetricCard(
    shape: RoundedCornerShape,
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TileMetrics.MinHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(TileMetrics.Padding),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = " " + unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}
