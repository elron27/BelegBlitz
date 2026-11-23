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
import de.belegblitz.ui.detail.BescheidAnsichtScreen
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
import de.belegblitz.domain.Kasse
import de.belegblitz.domain.RechnungsFilter
import de.belegblitz.domain.Reiter
import de.belegblitz.domain.StatusGruppe
import de.belegblitz.ui.list.InvoiceListScreen
import de.belegblitz.ui.auswertung.SucheScreen
import de.belegblitz.ui.auswertung.UebersichtScreen
import de.belegblitz.ui.settings.SettingsScreen
import de.belegblitz.ui.settings.SettingsViewModel

object Routes {
    const val LISTE = "liste"
    const val EINSTELLUNGEN = "einstellungen"
    const val UEBERSICHT = "uebersicht"
    const val SUCHE = "suche"
    const val ERFASSUNG = "erfassung"
    const val PRUEFEN = "pruefen"
    const val BESCHEID = "bescheid"
    const val DETAIL = "rechnung/{id}"
    const val BESCHEIDE = "bescheide"

    /** SR-70a2f4475fe0: Bescheid-Ansicht. */
    const val BESCHEID_ANSICHT = "bescheid/{id}"

    /** SR-75f859f5adc5: gefilterte Rechnungsliste (jahr 0 = alle Jahre). */
    const val GEFILTERT = "rechnungen/{kasse}/{gruppe}/{jahr}"
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
    // SR-1571303342c4 Kriterium 3: Hauptziele stapeln sich nicht, Zurück führt zur Rechnungsliste
    fun zuReiter(reiter: Reiter) {
        if (reiter == Reiter.RECHNUNGEN) nav.popBackStack(Routes.LISTE, false)
        else nav.navigate(reiter.route) { launchSingleTop = true; popUpTo(Routes.LISTE) }
    }
    fun zuEinstellungen() = nav.navigate(Routes.EINSTELLUNGEN) { launchSingleTop = true }
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
                onSettings = { zuEinstellungen() },
                onNeu = { nav.navigate(Routes.ERFASSUNG) },
                onOeffnen = { nav.navigate("rechnung/$it") },
                onReiter = { zuReiter(it) },
            )
        }
        composable(Routes.GEFILTERT, arguments = listOf(navArgument("jahr") { type = NavType.IntType })) {
            val a = it.arguments
            val filter = RechnungsFilter(
                Kasse.valueOf(a?.getString("kasse") ?: Kasse.DAK.name),
                a?.getInt("jahr")?.takeIf { j -> j != 0 },
                StatusGruppe.valueOf(a?.getString("gruppe") ?: StatusGruppe.OFFEN.name),
            )
            InvoiceListScreen(
                container,
                onSettings = { zuEinstellungen() },
                onNeu = { nav.navigate(Routes.ERFASSUNG) },
                onOeffnen = { id -> nav.navigate("rechnung/$id") },
                onReiter = { zuReiter(it) },
                filter = filter,
                onFilterEntfernen = { nav.popBackStack(Routes.LISTE, false) },
            )
        }
        composable(Routes.BESCHEIDE) {
            BescheideScreen(
                container, onReiter = { zuReiter(it) }, onSettings = { zuEinstellungen() },
                onOeffnen = { nav.navigate("bescheid/$it") },
            )
        }
        composable(Routes.BESCHEID_ANSICHT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            BescheidAnsichtScreen(
                container, it.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() }, onRechnung = { id -> nav.navigate("rechnung/$id") },
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
        composable(Routes.UEBERSICHT) {
            UebersichtScreen(
                container,
                onReiter = { zuReiter(it) }, onSettings = { zuEinstellungen() },
                onZeile = { kasse, jahr, gruppe -> nav.navigate("rechnungen/${kasse.name}/${gruppe.name}/${jahr ?: 0}") },
            )
        }
        composable(Routes.SUCHE) {
            SucheScreen(
                container, onReiter = { zuReiter(it) }, onSettings = { zuEinstellungen() },
                onOeffnen = { nav.navigate("rechnung/$it") },
            )
        }
        composable(Routes.EINSTELLUNGEN) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}