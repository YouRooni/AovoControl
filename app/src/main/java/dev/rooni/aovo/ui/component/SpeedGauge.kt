package dev.rooni.aovo.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedGauge(
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

    // upper three quarters of the square it is drawn in. Centring that square would leave
    // the dial looking like it had floated up, so the whole thing is nudged down by the
    // amount the arc is off centre: the arc spans from -r to +r/2, whose midpoint is a
    // quarter of a radius above the middle.
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

            // Inner battery ring.
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

            // Tick marks every 10% of the scale.
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
                // Live figures earn the accent colour; a standing "not connected" does not.
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

private fun formatSpeed(value: Float): String =
    if (value >= 100f) value.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)

private const val ARC_CENTRE_CORRECTION = 0.055f
