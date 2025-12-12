package com.jefino.frameworkforge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jefino.frameworkforge.ui.screens.ConfigScreen
import com.jefino.frameworkforge.ui.screens.DashboardScreen
import com.jefino.frameworkforge.ui.screens.ProgressScreen
import com.jefino.frameworkforge.ui.screens.SettingsScreen
import com.jefino.frameworkforge.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Config : Screen("config")
    data object Progress : Screen("progress")
    data object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToConfig = {
                    navController.navigate(Screen.Config.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToProgress = {
                    navController.navigate(Screen.Progress.route) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(Screen.Config.route) {
            ConfigScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onStartPatching = {
                    navController.navigate(Screen.Progress.route) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(Screen.Progress.route) {
            ProgressScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack(Screen.Dashboard.route, false)
                },
                onComplete = {
                    navController.popBackStack(Screen.Dashboard.route, false)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
