package dev.rooni.aovo.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rooni.aovo.data.GaugeStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SpeedGauge(
    speed: Float,
    maxSpeed: Float,
    battery: Int,
    unit: String,
    subtitle: String,
    active: Boolean,
    style: GaugeStyle = GaugeStyle.CLASSIC,
    modifier: Modifier = Modifier,
) {
    if (style == GaugeStyle.EXPRESSIVE) {
        ExpressiveSpeedGauge(
            speed = speed,
            maxSpeed = maxSpeed,
            battery = battery,
            unit = unit,
            subtitle = subtitle,
            active = active,
            modifier = modifier,
        )
    } else {
        ClassicSpeedGauge(
            speed = speed,
            maxSpeed = maxSpeed,
            battery = battery,
            unit = unit,
            subtitle = subtitle,
            active = active,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicSpeedGauge(
    speed: Float,
    maxSpeed: Float,
    battery: Int,
    unit: String,
    subtitle: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val sweep = 240f
    val startAngle = 150f

    val target = if (maxSpeed <= 0f) 0f else (speed / maxSpeed).coerceIn(0f, 1f)
    val animatedSpeed by animateFloatAsState(
        targetValue = target,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "speed",
    )
    val animatedBattery by animateFloatAsState(
        targetValue = (battery / 100f).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "battery",
    )
    val dim by animateFloatAsState(
        targetValue = if (active) 1f else 0.35f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "dim",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val batteryColor = when {
        battery <= 15 -> MaterialTheme.colorScheme.error
        battery <= 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { translationY = size.height * ARC_CENTRE_CORRECTION },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2f + size.minDimension * 0.02f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (animatedSpeed > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to primary.copy(alpha = dim),
                        0.5f to tertiary.copy(alpha = dim),
                        1f to primary.copy(alpha = dim),
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweep * animatedSpeed,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            val innerInset = inset + stroke * 1.6f
            val innerStroke = stroke * 0.42f
            val innerSize = Size(size.width - innerInset * 2, size.height - innerInset * 2)
            drawArc(
                color = trackColor.copy(alpha = 0.6f),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                style = Stroke(width = innerStroke, cap = StrokeCap.Round),
            )
            if (animatedBattery > 0.001f) {
                drawArc(
                    color = batteryColor.copy(alpha = dim),
                    startAngle = startAngle,
                    sweepAngle = sweep * animatedBattery,
                    useCenter = false,
                    topLeft = Offset(innerInset, innerInset),
                    size = innerSize,
                    style = Stroke(width = innerStroke, cap = StrokeCap.Round),
                )
            }

            val radius = min(size.width, size.height) / 2f - inset
            val center = Offset(size.width / 2f, size.height / 2f)
            repeat(13) { index ->
                val fraction = index / 12f
                val angle = Math.toRadians((startAngle + sweep * fraction).toDouble())
                val major = index % 3 == 0
                val outer = radius - stroke * 0.75f
                val inner = outer - if (major) stroke * 0.5f else stroke * 0.26f
                drawLine(
                    color = trackColor,
                    start = center + Offset(
                        (cos(angle) * inner).toFloat(),
                        (sin(angle) * inner).toFloat()
                    ),
                    end = center + Offset(
                        (cos(angle) * outer).toFloat(),
                        (sin(angle) * outer).toFloat()
                    ),
                    strokeWidth = if (major) stroke * 0.16f else stroke * 0.09f,
                    cap = StrokeCap.Round,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = formatSpeed(speed),
                fontSize = 68.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveSpeedGauge(
    speed: Float,
    maxSpeed: Float,
    battery: Int,
    unit: String,
    subtitle: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val targetSpeedFraction = if (maxSpeed <= 0f) 0f else (speed / maxSpeed).coerceIn(0f, 1f)
    val animatedSpeed by animateFloatAsState(
        targetValue = targetSpeedFraction,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "expSpeed",
    )
    val animatedBattery by animateFloatAsState(
        targetValue = (battery / 100f).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "expBattery",
    )
    val dim by animateFloatAsState(
        targetValue = if (active) 1f else 0.35f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "expDim",
    )

    val cornerPercent = (50f - animatedSpeed * 34f).roundToInt().coerceIn(16, 50)
    val squircleShape = RoundedCornerShape(cornerPercent)

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val trackBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val batteryColor = when {
        battery <= 15 -> MaterialTheme.colorScheme.error
        battery <= 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Left: Thick Vertical Speed Progress Bar across full height
        VerticalPillBar(
            fraction = animatedSpeed,
            brush = Brush.verticalGradient(
                listOf(tertiary.copy(alpha = dim), primary.copy(alpha = dim))
            ),
            trackColor = trackBg,
            icon = Icons.Filled.Speed,
            accentColor = primary,
            active = active,
            label = formatSpeed(speed),
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight(),
        )

        // Center: Speed pod on top, subtitle status on bottom
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Upper center: Morphing Squircle Speed Indicator (no border, large and prominent)
            Surface(
                shape = squircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f * dim + 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.35f)
                    .graphicsLayer {
                        shadowElevation = (animatedSpeed * 8f).dp.toPx()
                        shape = squircleShape
                        clip = false
                    },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = formatSpeed(speed),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                            style = MaterialTheme.typography.displayLarge,
                            lineHeight = 56.sp,
                        )
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                        )
                    }
                }
            }

            // Lower center: Subtitle status & voltage without being crowded
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }

        // Right: Thick Vertical Battery Progress Bar across full height
        VerticalPillBar(
            fraction = animatedBattery,
            brush = Brush.verticalGradient(
                listOf(batteryColor.copy(alpha = dim), batteryColor.copy(alpha = dim * 0.85f))
            ),
            trackColor = trackBg,
            icon = if (battery <= 20) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull,
            accentColor = batteryColor,
            active = active,
            label = "$battery%",
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun VerticalPillBar(
    fraction: Float,
    brush: Brush,
    trackColor: Color,
    icon: ImageVector,
    accentColor: Color,
    active: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(16.dp)

    val iconTint by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            fraction >= 0.78f -> MaterialTheme.colorScheme.onPrimary
            active -> accentColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        label = "iconTint",
    )

    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            fraction >= 0.12f -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "textColor",
    )

    Box(
        modifier = modifier
            .background(trackColor, pillShape)
            .clip(pillShape),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Bottom-up progress fill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction.coerceIn(0f, 1f))
                .background(brush),
        )
        // Overlay content: icon on top, value on bottom inside the pill
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatSpeed(value: Float): String =
    if (value >= 100f) value.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)

private const val ARC_CENTRE_CORRECTION = 0.055f
