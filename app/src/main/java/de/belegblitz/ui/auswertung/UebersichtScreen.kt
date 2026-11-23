package de.belegblitz.ui.auswertung

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import de.belegblitz.domain.StatusEtikett
import kotlin.math.roundToInt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.domain.Reiter
import de.belegblitz.ui.ReiterLeiste
import de.belegblitz.ui.UeberlaufMenue
import de.belegblitz.domain.Auswertung
import de.belegblitz.domain.AuswertungsZeile
import de.belegblitz.domain.BeitragsEintrag
import de.belegblitz.domain.Geld
import de.belegblitz.domain.Kasse
import de.belegblitz.domain.StatusGruppe
import de.belegblitz.domain.VkbJahresbilanz
import java.time.LocalDate

/**
 * SR-2b75d02ae8a8 (live per Room-Flow), SR-d109d26f3f86 (Filter), SR-5b4224df66fd (VKB-Jahresbilanz).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UebersichtScreen(
    container: AppContainer,
    onReiter: (Reiter) -> Unit,
    onSettings: () -> Unit,
    onZeile: (Kasse, Int?, StatusGruppe) -> Unit,
) {
    val db = container.db
    val rechnungen by remember { db.rechnungDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val einreichungen by remember { db.einreichungDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val personen by remember { db.personDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val verlauf by remember { db.vkbBeitragDao().alle() }.collectAsStateWithLifecycle(emptyList())

    val zeilen by remember {
        derivedStateOf {
            val jeId = rechnungen.associateBy { it.id }
            einreichungen.mapNotNull { e ->
                val r = jeId[e.rechnungId] ?: return@mapNotNull null
                AuswertungsZeile(r.personId, r.rechnungsdatum.year, e.kasse, e.status, e.betragCent, e.erstatteterBetragCent, r.id)
            }
        }
    }
    var jahr by remember { mutableStateOf<Int?>(null) }
    var kasse by remember { mutableStateOf<Kasse?>(null) }
    var person by remember { mutableStateOf<Long?>(null) }

    val gefiltert = Auswertung.filtern(zeilen, jahr, kasse, person)
    val summen = Auswertung.jeKasse(gefiltert)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.overview_title)) },
                actions = { UeberlaufMenue(onSettings) },
            )
        },
        bottomBar = { ReiterLeiste(Reiter.UEBERSICHT, onReiter) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.filter_year), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(jahr == null, { jahr = null }, { Text(stringResource(R.string.filter_all)) })
                        Auswertung.jahre(zeilen).forEach { j -> FilterChip(jahr == j, { jahr = j }, { Text("$j") }) }
                    }
                    Text(stringResource(R.string.filter_fund), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(kasse == null, { kasse = null }, { Text(stringResource(R.string.filter_all)) })
                        Kasse.entries.forEach { k -> FilterChip(kasse == k, { kasse = k }, { Text(k.name) }) }
                    }
                    // SR-d109d26f3f86 Kriterium 2: Person-Filter erst bei mehreren Personen
                    if (Auswertung.personenFilterSichtbar(personen.size)) {
                        Text(stringResource(R.string.filter_person), style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(person == null, { person = null }, { Text(stringResource(R.string.filter_all)) })
                            personen.forEach { p -> FilterChip(person == p.id, { person = p.id }, { Text(p.name) }) }
                        }
                    }
                }
            }
            Kasse.entries.filter { kasse == null || it == kasse }.forEach { k ->
                item(key = k.name) {
                    val s = summen.getValue(k)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(k.name, style = MaterialTheme.typography.titleMedium)
                            // SR-17cd13ad9ebd: große Zahl „Erstattet“ und Fortschrittsbalken erstattet/eingereicht
                            Text(stringResource(R.string.sum_refunded), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Geld.formatEuro(s.erstattetCent), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            val anteil = StatusEtikett.fortschritt(s.erstattetCent, s.eingereichtCent)
                            val prozent = stringResource(R.string.label_progress, (anteil * 100).roundToInt())
                            LinearProgressIndicator(
                                progress = { anteil },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { contentDescription = prozent },
                            )
                            // SR-75f859f5adc5: Zeilen öffnen die gefilterte Rechnungsliste
                            ZeileBetrag(R.string.sum_submitted, s.eingereichtCent) { onZeile(k, jahr, StatusGruppe.EINGEREICHT) }
                            ZeileBetrag(R.string.sum_refunded, s.erstattetCent) { onZeile(k, jahr, StatusGruppe.ERSTATTET) }
                            ZeileBetrag(R.string.sum_open, s.offenCent) { onZeile(k, jahr, StatusGruppe.OFFEN) }
                            // SR-d840886a42a1: nicht erstattet (abgelehnt / Rest bei Teilerstattung)
                            ZeileBetrag(R.string.sum_not_refunded, s.nichtErstattetCent) { onZeile(k, jahr, StatusGruppe.NICHT_ERSTATTET) }
                        }
                    }
                }
            }
            // SR-5b4224df66fd: VKB-Jahresbilanz (für das gewählte Jahr, sonst das laufende)
            if (kasse == null || kasse == Kasse.VKB) item(key = "bilanz") {
                val heute = LocalDate.now()
                val bilanzJahr = jahr ?: heute.year
                val vkb = Auswertung.filtern(zeilen, bilanzJahr, Kasse.VKB, person)
                val b = VkbJahresbilanz.berechnen(
                    bilanzJahr, verlauf.map { BeitragsEintrag(it.gueltigAb, it.monatsbetragCent) },
                    Auswertung.summe(vkb).erstattetCent, heute,
                )
                val container = if (b.hereingeholt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                val onContainer = if (b.hereingeholt) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.vkb_balance_title, b.jahr), style = MaterialTheme.typography.titleMedium)
                        if (b.laufendesJahr) {
                            Text(stringResource(R.string.vkb_balance_running, b.monate), style = MaterialTheme.typography.bodySmall)
                        }
                        ZeileBetrag(R.string.vkb_contributions, b.beitraegeCent)
                        ZeileBetrag(R.string.vkb_refunds, b.erstattetCent)
                        Text(stringResource(R.string.vkb_difference), style = MaterialTheme.typography.labelMedium)
                        Text(Geld.formatEuro(b.differenzCent), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(if (b.hereingeholt) R.string.vkb_recovered_yes else R.string.vkb_recovered_no),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZeileBetrag(label: Int, cent: Long, onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp) else Modifier.fillMaxWidth()
    Row(m, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label))
        Text(Geld.formatEuro(cent), style = MaterialTheme.typography.titleSmall)
    }
}
