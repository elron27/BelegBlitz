package de.belegblitz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.domain.BescheidAnsicht
import de.belegblitz.domain.BescheidKopf
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.Reiter
import de.belegblitz.domain.Titel

/** SR-1d598be18236: alle Bescheide, neueste zuerst, mit Kasse, Datum und zugeordneten Rechnungen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BescheideScreen(container: AppContainer, onReiter: (Reiter) -> Unit, onSettings: () -> Unit, onOeffnen: (Long) -> Unit) {
    val db = container.db
    val bescheide by remember { db.bescheidDao().alleNeuesteZuerst() }.collectAsStateWithLifecycle(emptyList())
    val positionen by remember { db.bescheidDao().allePositionen() }.collectAsStateWithLifecycle(emptyList())
    val rechnungen by remember { db.rechnungDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val titelJeId = rechnungen.associate { it.id to it.titel }
    val karten = BescheidAnsicht.karten(
        bescheide.map { BescheidKopf(it.id, it.kasse, it.datum, it.dokumentPfad) },
        positionen.map { it.bescheidId to it.rechnungId },
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_notices)) }, actions = { UeberlaufMenue(onSettings) }) },
        bottomBar = { ReiterLeiste(Reiter.BESCHEIDE, onReiter) },
    ) { padding ->
        if (karten.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.notices_empty), textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(karten, key = { it.bescheid.id }) { k ->
                    val b = k.bescheid
                    // SR-70a2f4475fe0 Kriterium 1: Klick öffnet die Bescheid-Ansicht
                    Card(Modifier.fillMaxWidth().clickable { onOeffnen(b.id) }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.notice_card_title, b.kasse.name, b.datum.format(RechnungEingabe.DatumFormat)),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (k.rechnungIds.isEmpty()) {
                                Text(stringResource(R.string.notice_no_invoices), style = MaterialTheme.typography.bodySmall)
                            }
                            k.rechnungIds.forEach { id ->
                                Text(titelJeId[id]?.let { Titel.fuerAnzeige(it) } ?: "#$id", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
