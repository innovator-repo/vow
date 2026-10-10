package com.vow.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.screens.home.HomeScreen
import com.vow.app.ui.screens.setup.SetupScreen
import com.vow.app.ui.screens.setup.PasswordSetupScreen
import com.vow.app.ui.screens.settings.RestrictionsScreen
import com.vow.app.ui.screens.settings.KeywordBlockScreen
import com.vow.app.ui.screens.settings.SealScreen
import com.vow.app.ui.screens.profiles.ProfilesScreen
import com.vow.app.ui.screens.profiles.ProfileEditScreen
import com.vow.app.ui.screens.night.NightModeScreen
import com.vow.app.ui.screens.stats.StatsScreen
import com.vow.app.ui.screens.bypass.BypassScreen
import com.vow.app.ui.screens.bypass.BreathingScreen
import com.vow.app.ui.screens.settings.AppRulesScreen
import com.vow.app.ui.screens.settings.CoolingOffScreen

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object PasswordSetup : Screen("password_setup")
    object Home : Screen("home")
    object Restrictions : Screen("restrictions")
    object KeywordBlock : Screen("keyword_block")
    object Seal : Screen("seal/{restrictionId}") {
        fun createRoute(restrictionId: String) = "seal/$restrictionId"
    }
    object Profiles : Screen("profiles")
    object ProfileEdit : Screen("profile_edit/{profileId}") {
        fun createRoute(profileId: String) = "profile_edit/$profileId"
    }
    object NightMode : Screen("night_mode")
    object Stats : Screen("stats")
    object Bypass : Screen("bypass/{restrictionId}") {
        fun createRoute(restrictionId: String) = "bypass/$restrictionId"
    }
    object Breathing : Screen("breathing/{restrictionId}") {
        fun createRoute(restrictionId: String) = "breathing/$restrictionId"
    }
    object AppRules : Screen("app_rules")
    object CoolingOff : Screen("cooling_off")
}

@Composable
fun VowNavHost(
    navController: NavHostController,
    repo: VowRepository,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(260))
        },
        exitTransition = { scaleOut(tween(320), targetScale = 0.96f) + fadeOut(tween(200)) },
        popEnterTransition = { scaleIn(tween(320), initialScale = 0.96f) + fadeIn(tween(260)) },
        popExitTransition = {
            slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 5 } + fadeOut(tween(200))
        }
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                repo = repo,
                onSetupComplete = {
                    navController.navigate(Screen.PasswordSetup.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PasswordSetup.route) {
            PasswordSetupScreen(
                repo = repo,
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.PasswordSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                repo = repo,
                onNavigateToRestrictions = { navController.navigate(Screen.Restrictions.route) },
                onNavigateToProfiles = { navController.navigate(Screen.Profiles.route) },
                onNavigateToNightMode = { navController.navigate(Screen.NightMode.route) },
                onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                onNavigateToAppRules = { navController.navigate(Screen.AppRules.route) },
                onNavigateToCoolingOff = { navController.navigate(Screen.CoolingOff.route) }
            )
        }

        composable(Screen.Restrictions.route) {
            RestrictionsScreen(
                repo = repo,
                onNavigateToSeal = { id -> navController.navigate(Screen.Seal.createRoute(id)) },
                onNavigateToKeywords = { navController.navigate(Screen.KeywordBlock.route) },
                onNavigateToBypass = { id -> navController.navigate(Screen.Bypass.createRoute(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.KeywordBlock.route) {
            KeywordBlockScreen(
                repo = repo,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Seal.route) { backStackEntry ->
            val restrictionId = backStackEntry.arguments?.getString("restrictionId") ?: ""
            SealScreen(
                repo = repo,
                restrictionId = restrictionId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profiles.route) {
            ProfilesScreen(
                repo = repo,
                onEditProfile = { id -> navController.navigate(Screen.ProfileEdit.createRoute(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ProfileEdit.route) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: "new"
            ProfileEditScreen(
                repo = repo,
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.NightMode.route) {
            NightModeScreen(
                repo = repo,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Stats.route) {
            StatsScreen(
                repo = repo,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Bypass.route) { backStackEntry ->
            val restrictionId = backStackEntry.arguments?.getString("restrictionId") ?: ""
            BypassScreen(
                repo = repo,
                restrictionId = restrictionId,
                onStartBreathing = {
                    navController.navigate(Screen.Breathing.createRoute(restrictionId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Breathing.route) { backStackEntry ->
            val restrictionId = backStackEntry.arguments?.getString("restrictionId") ?: ""
            BreathingScreen(
                repo = repo,
                restrictionId = restrictionId,
                onComplete = {
                    navController.popBackStack(Screen.Home.route, false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppRules.route) {
            AppRulesScreen(
                repo = repo,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CoolingOff.route) {
            CoolingOffScreen(
                repo = repo,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
