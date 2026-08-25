package dev.rooni.aovo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Terminal
import dev.rooni.aovo.ui.screen.ManualCommandDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.rooni.aovo.R
import dev.rooni.aovo.ui.screen.ControllerScreen
import dev.rooni.aovo.ui.screen.DashboardScreen
import dev.rooni.aovo.ui.screen.DataScreen
import dev.rooni.aovo.ui.screen.DevicesSheet
import dev.rooni.aovo.ui.screen.FirmwareScreen
import dev.rooni.aovo.ui.screen.EngineeringScreen
import dev.rooni.aovo.ui.screen.ModuleScreen
import dev.rooni.aovo.ui.screen.PasswordDialog
import dev.rooni.aovo.ui.screen.ProfilesScreen
import dev.rooni.aovo.ui.screen.RideSettingsScreen
import dev.rooni.aovo.ui.screen.AboutScreen
import dev.rooni.aovo.ui.screen.SettingsScreen

private object Routes {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AovoAppScaffold(viewModel: AovoViewModel) {
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

    // Lives here rather than in the screen because the button that opens it is in the bar.
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
                // part of the page; a shallow lift puts it back on its own layer.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    // change, so a screen change does not read as the bar blinking.
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
                    // Probing unmapped commands belongs with the engineering menu and
                    // nowhere else, so the button appears only on that screen.
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
            // frame, so the page slides down with the bar instead of jumping once it goes.
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
            // the order of the bottom bar, while opening a detail screen always comes in
            // from the trailing edge.
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
            composable(Routes.ABOUT) { AboutScreen() }
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
