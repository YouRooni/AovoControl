package dev.rooni.aovo.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rooni.aovo.BuildConfig
import dev.rooni.aovo.R
import dev.rooni.aovo.ui.AovoViewModel
import dev.rooni.aovo.ui.Haptic
import dev.rooni.aovo.ui.LocalHaptics
import dev.rooni.aovo.ui.UpdateState
import dev.rooni.aovo.ui.component.Section
import dev.rooni.aovo.ui.component.SectionTitle
import dev.rooni.aovo.ui.component.SettingRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val AUTHOR_URL = "https://t.me/YouRooni"
private const val CHANNEL_URL = "https://t.me/RnPlugins"
private const val DONATE_URL = "https://t.me/payRooni"
private const val GITHUB_URL = "https://github.com/YouRooni/AovoControl"
private const val FORUM_URL = "https://4pda.to/forum/index.php?showtopic=1125489"
private const val CONTRIBUTOR_URL = "https://t.me/vova7878"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(viewModel: AovoViewModel? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()

    var easterEggTrigger by remember { mutableLongStateOf(0L) }
    val updateState = viewModel?.updateState?.collectAsStateWithLifecycle()?.value ?: UpdateState.Idle

    fun openUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.90f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "iconScale"
            )

            Surface(
                modifier = Modifier
                    .size(92.dp)
                    .scale(scale)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { haptics?.perform(Haptic.Press) },
                        onLongClick = {
                            easterEggTrigger = System.currentTimeMillis()
                            scope.launch {
                                haptics?.perform(Haptic.Heavy)
                                delay(90)
                                haptics?.perform(Haptic.ToggleOn)
                                delay(90)
                                haptics?.perform(Haptic.Confirm)
                            }
                        }
                    ),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                        modifier = Modifier.size(80.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(6.dp))

            // Version badge row with refresh icon button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                if (viewModel != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            haptics?.perform(Haptic.Press)
                            viewModel.checkForUpdates(isManual = true)
                        },
                        enabled = updateState !is UpdateState.Checking,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (updateState is UpdateState.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.check_updates),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.about_app_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            )

            Spacer(Modifier.height(20.dp))

            // Developer Section
            SectionTitle(stringResource(R.string.developer))
            Section {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.author),
                        subtitle = stringResource(R.string.contact_developer),
                        value = "@YouRooni",
                        icon = Icons.Filled.Person,
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { openUrl(AUTHOR_URL) },
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.telegram_channel),
                        subtitle = stringResource(R.string.telegram_channel_desc),
                        value = "@RnPlugins",
                        icon = Icons.Filled.Campaign,
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { openUrl(CHANNEL_URL) },
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.donate),
                        subtitle = stringResource(R.string.donate_desc),
                        icon = Icons.Filled.Favorite,
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { openUrl(DONATE_URL) },
                    )
                }
            }

            // Project Links Section
            SectionTitle(stringResource(R.string.project_links))
            Section {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.github),
                        subtitle = stringResource(R.string.github_desc),
                        icon = Icons.Filled.Code,
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { openUrl(GITHUB_URL) },
                    )
                }
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.fourpda),
                        subtitle = stringResource(R.string.fourpda_desc),
                        icon = Icons.Filled.Forum,
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { openUrl(FORUM_URL) },
                    )
                }
            }

            // Community Credits Section
            SectionTitle(stringResource(R.string.community_credits))
            Section {
                tile { shape ->
                    SettingRow(
                        shape = shape,
                        title = stringResource(R.string.credits_research),
                        subtitle = stringResource(R.string.credits_research_desc),
                        value = "@vova7878",
                        icon = Icons.Filled.Handshake,
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { openUrl(CONTRIBUTOR_URL) },
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }

        // Easter Egg Fireworks & Confetti Overlay
        if (easterEggTrigger > 0L) {
            ConfettiFirework(
                trigger = easterEggTrigger,
                onFinished = { easterEggTrigger = 0L }
            )
        }

        if (updateState is UpdateState.UpToDate && updateState.isManual) {
            AlertDialog(
                onDismissRequest = { viewModel?.dismissUpdate() },
                title = { Text(stringResource(R.string.latest_version_installed), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.latest_version_installed_desc, BuildConfig.VERSION_NAME)) },
                confirmButton = {
                    TextButton(onClick = { viewModel?.dismissUpdate() }) {
                        Text(stringResource(R.string.great))
                    }
                }
            )
        }

        if (updateState is UpdateState.Error && updateState.isManual) {
            AlertDialog(
                onDismissRequest = { viewModel?.dismissUpdate() },
                title = { Text(stringResource(R.string.update_check_failed), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.update_check_failed_desc)) },
                confirmButton = {
                    TextButton(onClick = { viewModel?.dismissUpdate() }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }
    }
}

private data class Particle(
    val angle: Double,
    val speed: Float,
    val color: Color,
    val size: Float,
    val isCircle: Boolean,
    val rotationSpeed: Float,
)

@Composable
private fun ConfettiFirework(trigger: Long, onFinished: () -> Unit) {
    val progress = remember(trigger) { Animatable(0f) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val particles = remember(trigger) {
        val colors = listOf(
            primary, secondary, tertiary,
            Color(0xFFFF5722), Color(0xFFFFEB3B), Color(0xFF00E676),
            Color(0xFF00B0FF), Color(0xFFE040FB), Color(0xFFFF4081)
        )
        List(85) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val speed = Random.nextFloat() * 650f + 250f
            val color = colors[Random.nextInt(colors.size)]
            val size = Random.nextFloat() * 12f + 8f
            val isCircle = Random.nextBoolean()
            val rotationSpeed = (Random.nextFloat() - 0.5f) * 720f
            Particle(angle, speed, color, size, isCircle, rotationSpeed)
        }
    }

    LaunchedEffect(trigger) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1600, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = progress.value
        val origin = Offset(size.width / 2f, 70.dp.toPx())

        particles.forEach { p ->
            val distance = p.speed * t
            val gravity = 380f * t * t
            val x = origin.x + (cos(p.angle) * distance).toFloat()
            val y = origin.y + (sin(p.angle) * distance).toFloat() + gravity
            val alpha = (1f - t).coerceIn(0f, 1f)

            rotate(degrees = p.rotationSpeed * t, pivot = Offset(x, y)) {
                if (p.isCircle) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = (p.size / 2f) * (1f - t * 0.3f),
                        center = Offset(x, y),
                    )
                } else {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.size / 2f, y - p.size / 4f),
                        size = Size(p.size, p.size / 2f),
                    )
                }
            }
        }
    }
}
