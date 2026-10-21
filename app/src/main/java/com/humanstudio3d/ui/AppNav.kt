package com.humanstudio3d.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.humanstudio3d.ui.screens.ExportScreen
import com.humanstudio3d.ui.screens.HelpScreen
import com.humanstudio3d.ui.screens.HomeScreen
import com.humanstudio3d.ui.screens.PrivacyScreen
import com.humanstudio3d.ui.screens.ProcessingScreen
import com.humanstudio3d.ui.screens.ReviewScreen
import com.humanstudio3d.ui.screens.SettingsScreen
import com.humanstudio3d.ui.screens.ViewerScreen

private object Routes {
    const val HOME = "home"
    const val REVIEW = "review"
    const val PROCESSING = "processing"
    const val PRIVACY = "privacy"
    const val VIEWER = "viewer"
    const val EXPORT = "export"
    const val HELP = "help"
    const val SETTINGS = "settings"
}

@Composable
fun AppNav(vm: CharacterViewModel) {
    val nav = rememberNavController()

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                UiEvent.ImageReady -> nav.navigate(Routes.REVIEW)
                UiEvent.ModelReady -> nav.navigate(Routes.VIEWER)
            }
        }
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                onOpenHelp = { nav.navigate(Routes.HELP) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onReplaceOrCancel = {
                    vm.discardImage()
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
                onConfirm = {
                    vm.resetProcessing()
                    nav.navigate(Routes.PROCESSING)
                },
            )
        }
        composable(Routes.PROCESSING) {
            ProcessingScreen(
                vm = vm,
                onBack = {
                    vm.cancelProcessing()
                    nav.popBackStack()
                },
                onPickAnother = {
                    vm.discardImage()
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.VIEWER) {
            ViewerScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onExport = { nav.navigate(Routes.EXPORT) },
            )
        }
        composable(Routes.EXPORT) {
            ExportScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
