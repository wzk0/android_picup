package com.example.picup.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.picup.AppContainer
import com.example.picup.ui.history.HistoryPreviewScreen
import com.example.picup.ui.main.MainScreen
import com.example.picup.ui.settings.SettingsScreen
import com.example.picup.ui.settings.SettingsViewModel
import com.example.picup.ui.setup.SetupScreen
import com.example.picup.ui.theme.PicUpTheme

private const val RouteMain = "main"
private const val RouteSettings = "settings"
private const val RouteSetup = "setup"
private const val RoutePreview = "preview"

/**
 * Three destinations, one activity.
 *
 * Navigation Compose owns the back stack so the system's predictive-back
 * gesture can seek the pop transition frame by frame: `predictivePopEnter/
 * ExitTransition` deliberately use the same policy as `popEnter/popExit`, which
 * is what makes a swipe-back follow the finger instead of jumping at the end.
 *
 * Screens themselves stay dependency-free: they receive lambdas, never a
 * NavController.
 */
@Composable
fun PicUpRoot(container: AppContainer) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    // Each visit to the wizard gets a fresh ViewModel: adding a second
    // repository must not resume the previous wizard at step three.
    var setupSession by rememberSaveable { mutableIntStateOf(0) }

    fun openSetup() {
        setupSession += 1
        navController.navigate("$RouteSetup/$setupSession") {
            launchSingleTop = true
        }
    }

    // First frame grows in instead of snapping: the splash hands over to a
    // 240 ms scale/fade, which makes the hand-off feel like one motion.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        )
    }

    PicUpTheme(
        theme = settings.theme,
        dynamicColor = settings.dynamicColor,
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            NavHost(
                navController = navController,
                startDestination = RouteMain,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = entrance.value
                        alpha = progress
                        scaleX = 0.96f + 0.04f * progress
                        scaleY = 0.96f + 0.04f * progress
                    },
                // The preview is a window-level overlay: it rises over the
                // list instead of scaling like a page. Transition policy is
                // branch-per-destination, and the predictive variants mirror
                // it so a swipe-back seeks the same motion.
                enterTransition = {
                    if (targetState.isPreview) {
                        slideInVertically(tween(300)) { it } + fadeIn(tween(200))
                    } else {
                        pushEnter()
                    }
                },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { if (initialState.isPreview) fadeIn(tween(220)) else popEnter() },
                popExitTransition = { if (initialState.isPreview) previewExit() else popExit() },
                predictivePopEnterTransition = { if (initialState.isPreview) fadeIn(tween(220)) else popEnter() },
                predictivePopExitTransition = { if (initialState.isPreview) previewExit() else popExit() },
            ) {
                composable(RouteMain) {
                    MainScreen(
                        container = container,
                        onOpenSettings = { navController.navigate(RouteSettings) { launchSingleTop = true } },
                        onOpenSetup = ::openSetup,
                        onOpenPreview = { index ->
                            navController.navigate("$RoutePreview/$index") { launchSingleTop = true }
                        },
                    )
                }
                // The viewer owns the whole window, so it slides up over the
                // list instead of scaling like a page; predictive back slides
                // it back down with the gesture.
                composable(
                    route = "$RoutePreview/{index}",
                    arguments = listOf(navArgument("index") { type = NavType.IntType }),
                ) { entry ->
                    HistoryPreviewScreen(
                        container = container,
                        initialIndex = entry.arguments?.getInt("index") ?: 0,
                        onClose = { navController.popBackStack() },
                    )
                }
                composable(RouteSettings) {
                    SettingsScreen(
                        container = container,
                        viewModel = settingsViewModel,
                        onBack = { navController.popBackStack() },
                        onAddRemote = ::openSetup,
                    )
                }
                composable(
                    route = "$RouteSetup/{session}",
                    arguments = listOf(navArgument("session") { type = NavType.IntType }),
                ) { entry ->
                    val session = entry.arguments?.getInt("session") ?: 0
                    SetupScreen(
                        container = container,
                        sessionKey = session,
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private val NavFade = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private val NavScale = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Deeper screens grow in slightly; the list underneath does not move. */
private fun pushEnter(): EnterTransition =
    fadeIn(NavFade) + scaleIn(NavScale, initialScale = 0.92f)

private fun popEnter(): EnterTransition =
    fadeIn(NavFade) + scaleIn(NavScale, initialScale = 1.02f)

/** Popping shrinks the outgoing screen towards the centre, like the system's own preview. */
private fun popExit(): ExitTransition =
    fadeOut(NavFade) + scaleOut(NavScale, targetScale = 0.94f)

private fun previewExit(): ExitTransition =
    slideOutVertically(tween(260)) { it } + fadeOut(tween(200))

private val androidx.navigation.NavBackStackEntry.isPreview: Boolean
    get() = destination.route?.startsWith(RoutePreview) == true