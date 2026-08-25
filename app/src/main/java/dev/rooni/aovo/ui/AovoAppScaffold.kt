package dev.rooni.aovo.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.rooni.aovo.R
import dev.rooni.aovo.ui.screen.*

object Routes {
    const val DASHBOARD = "dashboard"
    const val DATA = "data"
    const val SETTINGS = "settings"
    const val RIDE = "ride"
    const val CONTROLLER = "controller"
    const val FIRMWARE = "firmware"
    const val MODULE = "module"
    const val ENGINEERING = "engineering"
    const val PROFILES = "profiles"
    const val ABOUT = "about"
}

private val topLevel = setOf(Routes.DASHBOARD, Routes.DATA, Routes.SETTINGS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AovoAppScaffold(viewModel: AovoViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.DASHBOARD

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbar by viewModel.snackbar.collectAsStateWithLifecycle()
    val passwordPrompt by viewModel.passwordPrompt.collectAsStateWithLifecycle()
    var showDevices by remember { mutableStateOf(false) }

    LaunchedEffect(snackbar) {
        val message = snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearSnackbar()
    }

    var manualCommand by remember { mutableStateOf(false) }
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(appBarState)

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val isScrolled = appBarState.contentOffset < -1f
            val shadowElevation by animateDpAsState(
                targetValue = if (isScrolled) 4.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "topBarShadow"
            )
            Surface(
                shadowElevation = shadowElevation,
                tonalElevation = if (isScrolled) 2.dp else 0.dp,
            ) {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    title = {
                        AnimatedContent(
                            targetState = titleFor(route),
                            transitionSpec = {
                                (fadeIn(tween(220, delayMillis = 60)) togetherWith
                                    fadeOut(tween(140))).using(SizeTransform(clip = false))
                            },
                            label = "title",
                        ) { title ->
                            Text(text = stringResource(title), fontWeight = FontWeight.SemiBold)
                        }
                    },
                    navigationIcon = {
                        AnimatedVisibility(
                            visible = route !in topLevel,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(140)),
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    actions = {
                        if (route == Routes.SETTINGS) {
                            IconButton(onClick = { navController.navigate(Routes.ABOUT) }) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = stringResource(R.string.about),
                                )
                            }
                        }
                        if (route == Routes.ENGINEERING) {
                            IconButton(onClick = { manualCommand = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Terminal,
                                    contentDescription = stringResource(R.string.manual_command),
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = route in topLevel,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    expandFrom = Alignment.Top,
                ) + fadeIn(tween(220, delayMillis = 40)),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(tween(140)),
            ) {
                Surface(shadowElevation = 8.dp) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                    ) {
                        NavigationBarItem(
                            selected = route == Routes.DASHBOARD,
                            onClick = { navController.switchTo(Routes.DASHBOARD) },
                            icon = { Icon(Icons.Filled.Speed, null) },
                            label = { Text(stringResource(R.string.nav_dashboard)) },
                        )
                        NavigationBarItem(
                            selected = route == Routes.DATA,
                            onClick = { navController.switchTo(Routes.DATA) },
                            icon = { Icon(Icons.Filled.Insights, null) },
                            label = { Text(stringResource(R.string.nav_data)) },
                        )
                        NavigationBarItem(
                            selected = route == Routes.SETTINGS,
                            onClick = { navController.switchTo(Routes.SETTINGS) },
                            icon = { Icon(Icons.Filled.Settings, null) },
                            label = { Text(stringResource(R.string.nav_settings)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
            enterTransition = {
                val forward = travelsForward(initialState, targetState)
                slideIntoContainer(
                    if (forward) SlideDirection.Start else SlideDirection.End,
                    tween(320),
                ) + fadeIn(tween(220))
            },
            exitTransition = {
                val forward = travelsForward(initialState, targetState)
                slideOutOfContainer(
                    if (forward) SlideDirection.Start else SlideDirection.End,
                    tween(320),
                ) + fadeOut(tween(180))
            },
            popEnterTransition = {
                val forward = travelsForward(initialState, targetState)
                slideIntoContainer(
                    if (forward) SlideDirection.Start else SlideDirection.End,
                    tween(320),
                ) + fadeIn(tween(220))
            },
            popExitTransition = {
                val forward = travelsForward(initialState, targetState)
                slideOutOfContainer(
                    if (forward) SlideDirection.Start else SlideDirection.End,
                    tween(320),
                ) + fadeOut(tween(180))
            },
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    viewModel = viewModel,
                    onOpenDevices = {
                        viewModel.startScan()
                        showDevices = true
                    },
                )
            }
            composable(Routes.DATA) { DataScreen(viewModel) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenRide = { navController.navigate(Routes.RIDE) },
                    onOpenController = { navController.navigate(Routes.CONTROLLER) },
                    onOpenFirmware = { navController.navigate(Routes.FIRMWARE) },
                    onOpenModule = { navController.navigate(Routes.MODULE) },
                    onOpenEngineering = { navController.navigate(Routes.ENGINEERING) },
                    onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                )
            }
            composable(Routes.RIDE) { RideSettingsScreen(viewModel) }
            composable(Routes.CONTROLLER) { ControllerScreen(viewModel) }
            composable(Routes.FIRMWARE) { FirmwareScreen(viewModel) }
            composable(Routes.MODULE) { ModuleScreen(viewModel) }
            composable(Routes.ENGINEERING) { EngineeringScreen(viewModel) }
            composable(Routes.PROFILES) { ProfilesScreen(viewModel) }
            composable(Routes.ABOUT) { AboutScreen(viewModel) }
        }
    }

    if (showDevices) {
        DevicesSheet(viewModel = viewModel, onDismiss = { showDevices = false })
    }

    passwordPrompt?.let { device ->
        PasswordDialog(
            device = device,
            onDismiss = viewModel::dismissPasswordPrompt,
            onConfirm = { viewModel.connect(device, it) },
        )
    }

    if (manualCommand) {
        ManualCommandDialog(viewModel) { manualCommand = false }
    }

    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val availableUpdate = (updateState as? UpdateState.Available)?.release
    if (availableUpdate != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.update_available),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.update_available_desc, availableUpdate.tagName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (availableUpdate.body.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = availableUpdate.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissUpdate()
                        val uri = Uri.parse(availableUpdate.htmlUrl)
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                ) {
                    Text(stringResource(R.string.update_view_release))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.ignoreUpdate(availableUpdate.tagName) }) {
                        Text(stringResource(R.string.update_skip_version))
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = viewModel::dismissUpdate) {
                        Text(stringResource(R.string.update_later))
                    }
                }
            }
        )
    }
}

private fun androidx.navigation.NavHostController.switchTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun travelOrder(route: String?): Int = when (route) {
    Routes.DASHBOARD -> 0
    Routes.DATA -> 1
    Routes.SETTINGS -> 2
    null -> 0
    else -> 100
}

private fun travelsForward(from: NavBackStackEntry, to: NavBackStackEntry): Boolean =
    travelOrder(to.destination.route) >= travelOrder(from.destination.route)

private fun titleFor(route: String): Int = when (route) {
    Routes.DATA -> R.string.nav_data
    Routes.SETTINGS -> R.string.nav_settings
    Routes.RIDE -> R.string.ride_settings
    Routes.CONTROLLER -> R.string.controller_params
    Routes.FIRMWARE -> R.string.firmware
    Routes.MODULE -> R.string.module
    Routes.ENGINEERING -> R.string.engineering_menu
    Routes.PROFILES -> R.string.profiles
    Routes.ABOUT -> R.string.about
    else -> R.string.app_name
}
