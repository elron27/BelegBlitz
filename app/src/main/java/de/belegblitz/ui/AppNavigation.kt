package de.belegblitz.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import de.belegblitz.ui.detail.InvoiceDetailScreen
import de.belegblitz.ui.erfassung.BelegArt
import de.belegblitz.ui.erfassung.BescheidPruefenScreen
import de.belegblitz.ui.erfassung.BescheidViewModel
import de.belegblitz.ui.erfassung.ErfassungScreen
import de.belegblitz.ui.erfassung.ErfassungViewModel
import de.belegblitz.ui.erfassung.PruefenScreen
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
    const val ERFASSUNG = "erfassung"
    const val PRUEFEN = "pruefen"
    const val BESCHEID = "bescheid"
    const val DETAIL = "rechnung/{id}"
}

@Composable
fun AppNavigation(container: AppContainer) {
    val nav = rememberNavController()
    // Gemeinsamer Zustand von Erfassen und Prüfen (an die Activity gebunden)
    val erfassung: ErfassungViewModel =
        viewModel(factory = ErfassungViewModel.factory(container, LocalContext.current))

    val bescheid: BescheidViewModel =
        viewModel(factory = BescheidViewModel.factory(container, LocalContext.current))

    // SR-4eb0053fe161: per Teilen/Öffnen eingegangenes PDF in denselben Ablauf übernehmen
    val eingang by container.eingehendesPdf.collectAsStateWithLifecycle()
    LaunchedEffect(eingang) {
        eingang?.let {
            container.eingehendesPdf.value = null
            erfassung.pdfImportieren(it)
            nav.navigate(Routes.ERFASSUNG) { launchSingleTop = true; popUpTo(Routes.LISTE) }
        }
    }
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
            InvoiceListScreen(
                container,
                onSettings = { nav.navigate(Routes.EINSTELLUNGEN) },
                onNeu = { nav.navigate(Routes.ERFASSUNG) },
                onOeffnen = { nav.navigate("rechnung/$it") },
            )
        }
        composable(Routes.ERFASSUNG) {
            ErfassungScreen(
                erfassung,
                onWeiter = {
                    // SR-3273b2e9382d: Bescheid oder Rechnung
                    if (erfassung.state.value.art == BelegArt.BESCHEID) nav.navigate(Routes.BESCHEID) else nav.navigate(Routes.PRUEFEN)
                },
                onAbbrechen = { nav.popBackStack(Routes.LISTE, false) },
            )
        }
        composable(Routes.PRUEFEN) {
            PruefenScreen(
                erfassung,
                onZurueck = { nav.popBackStack() },
                // Gelöste Seiten bilden die nächste Rechnung
                onGespeichert = { weitere ->
                    if (weitere) nav.popBackStack(Routes.ERFASSUNG, false) else nav.popBackStack(Routes.LISTE, false)
                },
            )
        }
        composable(Routes.BESCHEID) {
            BescheidPruefenScreen(
                erfassung, bescheid,
                onZurueck = { nav.popBackStack() },
                onGespeichert = { nav.popBackStack(Routes.LISTE, false) },
            )
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            InvoiceDetailScreen(container, it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
        composable(Routes.EINSTELLUNGEN) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}