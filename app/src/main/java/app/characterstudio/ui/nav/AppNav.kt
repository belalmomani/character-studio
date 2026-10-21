package app.characterstudio.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.characterstudio.editor.ui.EditorScreen
import app.characterstudio.export.ExportScreen
import app.characterstudio.ui.home.HomeScreen
import app.characterstudio.ui.info.InfoScreen
import app.characterstudio.ui.settings.SettingsScreen
import app.characterstudio.ui.newcharacter.NewCharacterScreen
import app.characterstudio.ui.projects.ProjectsScreen

object Routes {
    const val HOME = "home"
    const val PROJECTS = "projects"
    const val NEW = "new"
    const val SETTINGS = "settings"
    const val INFO = "info/{kind}"
    fun info(kind: String) = "info/$kind"
    const val EDITOR = "editor/{projectId}"
    fun editor(id: String) = "editor/$id"
    const val EXPORT = "export/{projectId}"
    fun export(id: String) = "export/$id"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onProjects = { nav.navigate(Routes.PROJECTS) },
                onNewCharacter = { nav.navigate(Routes.NEW) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PROJECTS) {
            ProjectsScreen(
                onBack = { nav.popBackStack() },
                onNewCharacter = { nav.navigate(Routes.NEW) },
                onOpen = { nav.navigate(Routes.editor(it)) },
            )
        }
        composable(Routes.NEW) {
            NewCharacterScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.navigate(Routes.editor(id)) { popUpTo(Routes.NEW) { inclusive = true } }
                },
            )
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
            EditorScreen(
                projectId = entry.arguments?.getString("projectId").orEmpty(),
                onExit = { nav.popBackStack() },
                onExport = { nav.navigate(Routes.export(it)) },
            )
        }
        composable(Routes.EXPORT, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
            ExportScreen(
                projectId = entry.arguments?.getString("projectId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onInfo = { nav.navigate(Routes.info(it)) })
        }
        composable(Routes.INFO, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
            InfoScreen(kind = entry.arguments?.getString("kind").orEmpty(), onBack = { nav.popBackStack() })
        }
    }
}
