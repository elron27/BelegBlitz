package de.belegblitz.ui.detail

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.data.PdfWerkzeug
import de.belegblitz.domain.BescheidAnsicht
import de.belegblitz.domain.BescheidKopf
import de.belegblitz.domain.Geld
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.Titel
import de.belegblitz.ui.ZurueckKnopf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.belegblitz.domain.Hinweis

/**
 * SR-70a2f4475fe0: Bescheid-Ansicht mit Kasse, Datum, zugeordneten Rechnungen samt Beträgen (Klick öffnet die Rechnung),
 * Seitenvorschau des Dokuments und dem Knopf „Extern öffnen“.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BescheidAnsichtScreen(
    container: AppContainer, id: Long, onBack: () -> Unit, onRechnung: (Long) -> Unit,
    onBearbeiten: (Long) -> Unit = {}, onGeloescht: () -> Unit = {},
) {
    val db = container.db
    val bescheid by remember(id) { db.bescheidDao().finde(id) }.collectAsStateWithLifecycle(null)
    val positionen by remember(id) { db.bescheidDao().positionenJeBescheid(id) }.collectAsStateWithLifecycle(emptyList())
    val rechnungen by remember { db.rechnungDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val titelJeId = rechnungen.associate { it.id to it.titel }
    val zeilen = BescheidAnsicht.rechnungsZeilen(positionen.map { it.rechnungId to it.betragCent })
    val kopf = bescheid?.let { BescheidKopf(it.id, it.kasse, it.datum, it.dokumentPfad) }
    val name = kopf?.takeIf { BescheidAnsicht.kannOeffnen(it) }?.dokumentPfad
    val seiten by produceState<List<Bitmap>?>(null, name) {
        if (name == null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                PdfWerkzeug.pdfSeiten(container.pdfDatei(name), 1080)
            } catch (e: Exception) {
                Log.e("BescheidAnsicht", "Bescheid nicht darstellbar: $name", e)
                emptyList()
            }
        }
    }
    val context = LocalContext.current
    var loeschenFrage by remember { mutableStateOf(false) }
    // Issue #16: Löschen mit Rückfrage, danach zurück zur Liste
    if (loeschenFrage) {
        LoeschenDialog(
            R.string.delete_notice_title, R.string.delete_notice_message,
            onBestaetigt = {
                loeschenFrage = false
                container.scope.launch {
                    container.speicher.bescheidLoeschen(id)
                    withContext(Dispatchers.Main) { onGeloescht() }
                }
            },
            onAbbruch = { loeschenFrage = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(kopf?.let { stringResource(R.string.notice_heading, it.kasse.name, it.datum.format(RechnungEingabe.DatumFormat)) }.orEmpty())
                },
                navigationIcon = { ZurueckKnopf(onClick = onBack) },
                actions = {
                    if (kopf != null) BearbeitenLoeschenAktionen({ onBearbeiten(id) }, { loeschenFrage = true })
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (kopf == null) {
                item { Text(stringResource(R.string.notice_not_found)) }
                return@LazyColumn
            }
            // Issue #17: Hinweis zum Bescheid
            item(key = "hinweis") {
                HinweisAbschnitt(bescheid?.hinweis) { neu ->
                    container.scope.launch { db.bescheidDao().hinweisSetzen(id, Hinweis.bereinigen(neu)) }
                }
            }
            item { Text(stringResource(R.string.notice_invoices_heading), style = MaterialTheme.typography.titleSmall) }
            if (zeilen.isEmpty()) item { Text(stringResource(R.string.notice_no_invoices), style = MaterialTheme.typography.bodyMedium) }
            zeilen.forEach { z ->
                item(key = "rechnung_${z.rechnungId}") {
                    Card(Modifier.fillMaxWidth().clickable { onRechnung(z.rechnungId) }) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                titelJeId[z.rechnungId]?.let { Titel.fuerAnzeige(it) } ?: "#${z.rechnungId}",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(Geld.formatEuro(z.betragCent), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (name == null) {
                item { Text(stringResource(R.string.notice_document_missing)) }
            } else {
                item {
                    Button(onClick = { dokumentOeffnen(context, container.pdfDatei(name)) }) {
                        Text(stringResource(R.string.notice_open_external))
                    }
                }
                val bilder = seiten
                if (bilder != null && bilder.isEmpty()) item { Text(stringResource(R.string.notice_pdf_error)) }
                bilder.orEmpty().forEachIndexed { i, bmp ->
                    item(key = "seite_$i") {
                        Image(
                            bmp.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
    }
}
