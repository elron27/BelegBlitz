package de.belegblitz.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.belegblitz.AppContainer
import de.belegblitz.ui.list.InvoiceListScreen
import de.belegblitz.ui.settings.SettingsScreen
import de.belegblitz.ui.settings.SettingsViewModel

object Routes {
    const val LISTE = "liste"
    const val EINSTELLUNGEN = "einstellungen"
}

@Composable
fun AppNavigation(container: AppContainer) {
    val nav = rememberNavController()
    // Kurze eigene Übergänge, sonst hakt die vorhersagende Zurück-Geste
    NavHost(
        navController = nav,
        startDestination = Routes.LISTE,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(220)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.9f) },
        predictivePopEnterTransition = { _ -> fadeIn(tween(220)) },
        predictivePopExitTransition = { _ -> fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.9f) },
    ) {
        composable(Routes.LISTE) {
            InvoiceListScreen(onSettings = { nav.navigate(Routes.EINSTELLUNGEN) })
        }
        composable(Routes.EINSTELLUNGEN) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}