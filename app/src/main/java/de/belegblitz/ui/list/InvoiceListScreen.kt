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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import de.belegblitz.domain.EinreichungStatus
import de.belegblitz.domain.EtikettArt
import de.belegblitz.domain.StatusEtikett
import de.belegblitz.ui.LeererZustand
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import de.belegblitz.ui.UeberlaufMenue

/** SR-8d2f40b64013: gespeicherte Rechnungen mit Titel, Datum und Betrag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    container: AppContainer,
    onSettings: () -> Unit,
    onNeu: () -> Unit,
    onOeffnen: (Long) -> Unit,
    onReiter: (Reiter) -> Unit,
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
        bottomBar = { ReiterLeiste(Reiter.RECHNUNGEN, onReiter) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invoices_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // SR-1571303342c4: Überlaufmenü nur mit Einstellungen (löst SR-010571e2f9d9 Kriterium 3 für Suche/Übersicht ab)
                actions = { UeberlaufMenue(onSettings) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNeu,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_invoice)) },
            )
        },
    ) { padding ->
        if (rechnungen.isEmpty() && filter == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                LeererZustand(
                    Icons.Filled.Description, stringResource(R.string.empty_invoices_title), stringResource(R.string.invoices_empty),
                    stringResource(R.string.empty_first_invoice), onNeu,
                )
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
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = entfernen) },
                        )
                    }
                    if (rechnungen.isEmpty()) {
                        item(key = "leer") { Text(stringResource(R.string.filter_empty)) }
                    }
                }
                items(rechnungen, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth().clickable { onOeffnen(r.id) }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.praxis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        stringResource(
                                            R.string.invoice_meta, r.fachrichtung,
                                            r.rechnungsdatum.format(RechnungEingabe.DatumFormat),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(Geld.formatEuro(r.betragCent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            val meine = einreichungen.filter { it.rechnungId == r.id }
                            if (meine.isNotEmpty()) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    meine.forEach { e -> StatusEtikettPille(e.kasse.name, e.status) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** SR-17cd13ad9ebd Kriterium 1: kleine Pille mit Kasse und Status; Farbe je Etikett-Art aus dem Material-Farbschema. */
@Composable
private fun StatusEtikettPille(kasse: String, status: EinreichungStatus) {
    val farben = MaterialTheme.colorScheme
    val (hintergrund, vordergrund) = when (StatusEtikett.art(status)) {
        EtikettArt.ERFASST -> farben.surfaceVariant to farben.onSurfaceVariant
        EtikettArt.EINGEREICHT -> farben.tertiaryContainer to farben.onTertiaryContainer
        EtikettArt.ERSTATTET -> farben.primaryContainer to farben.onPrimaryContainer
        EtikettArt.TEILWEISE -> farben.secondaryContainer to farben.onSecondaryContainer
        EtikettArt.ABGELEHNT -> farben.errorContainer to farben.onErrorContainer
    }
    val text = stringResource(
        when (status) {
            EinreichungStatus.ERFASST -> R.string.status_recorded
            EinreichungStatus.EINGEREICHT -> R.string.status_submitted
            EinreichungStatus.ERSTATTET -> R.string.status_refunded
            EinreichungStatus.TEILWEISE_ERSTATTET -> R.string.status_partly
            EinreichungStatus.ABGELEHNT -> R.string.status_rejected
        },
    )
    Surface(shape = RoundedCornerShape(50), color = hintergrund, contentColor = vordergrund) {
        Text("$kasse · $text", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), maxLines = 1)
    }
}
