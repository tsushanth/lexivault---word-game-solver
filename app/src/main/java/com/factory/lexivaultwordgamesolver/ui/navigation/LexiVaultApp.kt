package com.factory.lexivaultwordgamesolver.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.factory.lexivaultwordgamesolver.billing.BillingManager
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.data.WordRepository
import com.factory.lexivaultwordgamesolver.onboarding.OnboardingManager
import com.factory.lexivaultwordgamesolver.ui.LexiVaultViewModelFactory
import com.factory.lexivaultwordgamesolver.ui.finder.WordFinderScreen
import com.factory.lexivaultwordgamesolver.ui.finder.WordFinderViewModel
import com.factory.lexivaultwordgamesolver.ui.onboarding.OnboardingScreen
import com.factory.lexivaultwordgamesolver.ui.pattern.PatternSolverScreen
import com.factory.lexivaultwordgamesolver.ui.pattern.PatternSolverViewModel
import com.factory.lexivaultwordgamesolver.ui.paywall.PaywallScreen
import com.factory.lexivaultwordgamesolver.ui.paywall.PaywallViewModel
import com.factory.lexivaultwordgamesolver.ui.saved.SavedWordsScreen
import com.factory.lexivaultwordgamesolver.ui.saved.SavedWordsViewModel
import com.factory.lexivaultwordgamesolver.ui.settings.SettingsScreen
import com.factory.lexivaultwordgamesolver.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private sealed class LexiVaultDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Finder : LexiVaultDestination("finder", "Word Finder", Icons.Filled.Search)
    data object Pattern : LexiVaultDestination("pattern", "Crossword", Icons.Filled.Extension)
    data object Saved : LexiVaultDestination("saved", "Saved", Icons.Filled.Star)
    data object Settings : LexiVaultDestination("settings", "Settings", Icons.Filled.Settings)
}

private val bottomNavDestinations = listOf(
    LexiVaultDestination.Finder,
    LexiVaultDestination.Pattern,
    LexiVaultDestination.Saved,
    LexiVaultDestination.Settings
)

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_PAYWALL = "paywall"

@Composable
fun LexiVaultApp(
    repository: WordRepository,
    premiumManager: PremiumManager,
    billingManager: BillingManager,
    onboardingManager: OnboardingManager
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val factory = remember(repository, premiumManager, billingManager) {
        LexiVaultViewModelFactory(repository, premiumManager, billingManager)
    }

    // Resolved once at launch so we don't flash the onboarding flow to returning users.
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        startDestination = if (onboardingManager.hasCompletedOnboarding()) {
            LexiVaultDestination.Finder.route
        } else {
            ROUTE_ONBOARDING
        }
    }
    val resolvedStartDestination = startDestination ?: return

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val showBottomBar = bottomNavDestinations.any { destination ->
                currentDestination?.hierarchy?.any { it.route == destination.route } == true
            }
            if (showBottomBar) {
                val haptic = LocalHapticFeedback.current
                NavigationBar {
                    bottomNavDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = resolvedStartDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        coroutineScope.launch {
                            onboardingManager.setCompleted()
                            navController.navigate(LexiVaultDestination.Finder.route) {
                                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                            }
                            // Show the paywall once, right after onboarding, before the user starts searching.
                            navController.navigate(ROUTE_PAYWALL)
                        }
                    }
                )
            }
            composable(LexiVaultDestination.Finder.route) {
                val viewModel: WordFinderViewModel = viewModel(factory = factory)
                WordFinderScreen(viewModel, onUpgradeClick = { navController.navigate(ROUTE_PAYWALL) })
            }
            composable(LexiVaultDestination.Pattern.route) {
                val viewModel: PatternSolverViewModel = viewModel(factory = factory)
                PatternSolverScreen(viewModel, onUpgradeClick = { navController.navigate(ROUTE_PAYWALL) })
            }
            composable(LexiVaultDestination.Saved.route) {
                val viewModel: SavedWordsViewModel = viewModel(factory = factory)
                SavedWordsScreen(viewModel, onUpgradeClick = { navController.navigate(ROUTE_PAYWALL) })
            }
            composable(LexiVaultDestination.Settings.route) {
                val viewModel: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(viewModel, onUpgradeClick = { navController.navigate(ROUTE_PAYWALL) })
            }
            composable(ROUTE_PAYWALL) {
                val viewModel: PaywallViewModel = viewModel(factory = factory)
                PaywallScreen(viewModel, onClose = { navController.popBackStack() })
            }
        }
    }
}
