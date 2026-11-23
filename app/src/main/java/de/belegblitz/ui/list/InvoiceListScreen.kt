package de.belegblitz.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import androidx.compose.material3.InputChip
import de.belegblitz.domain.Auswertung
import de.belegblitz.domain.AuswertungsZeile
import de.belegblitz.domain.Reiter
import de.belegblitz.domain.RechnungsFilter
import de.belegblitz.domain.StatusGruppe
import de.belegblitz.ui.ReiterLeiste
import de.belegblitz.domain.Geld
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.Titel
import de.belegblitz.ui.TitelleistenKnopfGroesse

/** SR-8d2f40b64013: gespeicherte Rechnungen mit Titel, Datum und Betrag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    container: AppContainer,
    onSettings: () -> Unit,
    onUebersicht: () -> Unit,
    onSuche: () -> Unit,
    onNeu: () -> Unit,
    onOeffnen: (Long) -> Unit,
    onBescheide: () -> Unit,
    /** SR-75f859f5adc5: aktiver Filter (null = alle Rechnungen); onFilterEntfernen zeigt die ungefilterte Liste. */
    filter: RechnungsFilter? = null,
    onFilterEntfernen: () -> Unit = {},
) {
    val alle by remember { container.db.rechnungDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val einreichungen by remember { container.db.einreichungDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val rechnungen = if (filter == null) alle else {
        val jeId = alle.associateBy { it.id }
        val zeilen = einreichungen.mapNotNull { e ->
            val r = jeId[e.rechnungId] ?: return@mapNotNull null
            AuswertungsZeile(r.personId, r.rechnungsdatum.year, e.kasse, e.status, e.betragCent, e.erstatteterBetragCent, r.id)
        }
        val ids = Auswertung.rechnungIds(zeilen, filter)
        alle.filter { it.id in ids }
    }
    Scaffold(
        bottomBar = { ReiterLeiste(Reiter.RECHNUNGEN, onRechnungen = {}, onBescheide = onBescheide) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invoices_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    // SR-010571e2f9d9 (SR-e2f9d9): Überlaufmenü statt dreier Textknöpfe, damit die Überschrift einzeilig bleibt.
                    var menue by remember { mutableStateOf(false) }
                    val menueBeschreibung = stringResource(R.string.more_options)
                    IconButton(
                        onClick = { menue = true },
                        modifier = Modifier.size(TitelleistenKnopfGroesse).semantics { contentDescription = menueBeschreibung },
                    ) {
                        Text("⋮", fontSize = 24.sp)
                    }
                    DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.search_title)) }, onClick = { menue = false; onSuche() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.overview_title)) }, onClick = { menue = false; onUebersicht() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menue = false; onSettings() })
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNeu) { Text(stringResource(R.string.new_invoice)) }
        },
    ) { padding ->
        if (rechnungen.isEmpty() && filter == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.invoices_empty), textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filter != null) {
                    item(key = "filter") {
                        val gruppe = stringResource(
                            when (filter.gruppe) {
                                StatusGruppe.EINGEREICHT -> R.string.group_submitted
                                StatusGruppe.ERSTATTET -> R.string.group_refunded
                                StatusGruppe.OFFEN -> R.string.group_open
                                StatusGruppe.NICHT_ERSTATTET -> R.string.group_not_refunded
                            },
                        )
                        val text = if (filter.jahr == null) {
                            stringResource(R.string.filter_chip, filter.kasse.name, gruppe)
                        } else {
                            stringResource(R.string.filter_chip_year, filter.kasse.name, gruppe, filter.jahr.toString())
                        }
                        val entfernen = stringResource(R.string.filter_remove)
                        InputChip(
                            selected = true,
                            onClick = onFilterEntfernen,
                            label = { Text(text) },
                            trailingIcon = { Text("✕", modifier = Modifier.semantics { contentDescription = entfernen }) },
                        )
                    }
                    if (rechnungen.isEmpty()) {
                        item(key = "leer") { Text(stringResource(R.string.filter_empty)) }
                    }
                }
                items(rechnungen, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth().clickable { onOeffnen(r.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(Titel.fuerAnzeige(r.titel), style = MaterialTheme.typography.titleSmall)
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
