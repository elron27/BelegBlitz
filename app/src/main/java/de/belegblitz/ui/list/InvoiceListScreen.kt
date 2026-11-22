package de.belegblitz.ui.list

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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import de.belegblitz.domain.Geld
import de.belegblitz.domain.RechnungEingabe

/** SR-8d2f40b64013: gespeicherte Rechnungen mit Titel, Datum und Betrag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(container: AppContainer, onSettings: () -> Unit, onUebersicht: () -> Unit, onSuche: () -> Unit, onNeu: () -> Unit, onOeffnen: (Long) -> Unit) {
    val rechnungen by remember { container.db.rechnungDao().alle() }.collectAsStateWithLifecycle(emptyList())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invoices_title)) },
                actions = {
                    TextButton(onClick = onSuche) { Text(stringResource(R.string.search_title)) }
                    TextButton(onClick = onUebersicht) { Text(stringResource(R.string.overview_title)) }
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNeu) { Text(stringResource(R.string.new_invoice)) }
        },
    ) { padding ->
        if (rechnungen.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.invoices_empty), textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rechnungen, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth().clickable { onOeffnen(r.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(r.titel, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(
                                    R.string.invoice_line,
                                    r.rechnungsdatum.format(RechnungEingabe.DatumFormat),
                                    Geld.formatEuro(r.betragCent),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
