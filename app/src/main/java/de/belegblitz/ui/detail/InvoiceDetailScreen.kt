package de.belegblitz.ui.detail

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.flow.map
import de.belegblitz.domain.BescheidAnsicht
import de.belegblitz.domain.BescheidKopf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.ui.ZurueckKnopf
import de.belegblitz.data.PdfWerkzeug
import de.belegblitz.domain.Geld
import de.belegblitz.domain.RechnungEingabe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.belegblitz.domain.Hinweis

/** SR-8d2f40b64013: Detailansicht zeigt die PDF-Seiten als Bilder (PdfRenderer). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    container: AppContainer, id: Long, onBack: () -> Unit,
    onBearbeiten: (Long) -> Unit = {}, onGeloescht: () -> Unit = {},
) {
    val rechnung by remember(id) { container.db.rechnungDao().finde(id) }.collectAsStateWithLifecycle(null)
    val seiten by produceState<List<Bitmap>?>(null, rechnung?.pdfPfad) {
        val name = rechnung?.pdfPfad ?: return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                PdfWerkzeug.pdfSeiten(container.pdfDatei(name), 1080)
            } catch (e: Exception) {
                Log.e("Detail", "PDF nicht darstellbar: $name", e)
                emptyList()
            }
        }
    }
    val bescheide by remember(id) {
        container.db.bescheidDao().jeRechnung(id).map { l -> BescheidAnsicht.fuerRechnung(l.map { BescheidKopf(it.id, it.kasse, it.datum, it.dokumentPfad) }) }
    }.collectAsStateWithLifecycle(emptyList())
    var loeschenFrage by remember { mutableStateOf(false) }
    // Issue #16: Löschen mit Rückfrage, danach zurück zur Liste
    if (loeschenFrage) {
        LoeschenDialog(
            R.string.delete_invoice_title, R.string.delete_invoice_message,
            onBestaetigt = {
                loeschenFrage = false
                val r = rechnung ?: return@LoeschenDialog
                container.scope.launch {
                    container.speicher.rechnungLoeschen(r)
                    withContext(Dispatchers.Main) { onGeloescht() }
                }
            },
            onAbbruch = { loeschenFrage = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(de.belegblitz.domain.Titel.fuerAnzeige(rechnung?.titel.orEmpty())) },
                navigationIcon = { ZurueckKnopf(onClick = onBack) },
                actions = {
                    if (rechnung != null) BearbeitenLoeschenAktionen({ onBearbeiten(id) }, { loeschenFrage = true })
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rechnung?.let { r ->
                item {
                    Column {
                        Text(
                            stringResource(
                                R.string.invoice_line,
                                r.rechnungsdatum.format(RechnungEingabe.DatumFormat), Geld.formatEuro(r.betragCent),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text("${r.fachrichtung}, ${r.praxis}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            rechnung?.let { r -> item { EinreichungenAbschnitt(container, r) } }
            // Issue #17: Hinweis zur Rechnung
            rechnung?.let { r ->
                item(key = "hinweis") {
                    HinweisAbschnitt(r.hinweis) { neu ->
                        container.scope.launch { container.db.rechnungDao().hinweisSetzen(r.id, Hinweis.bereinigen(neu)) }
                    }
                }
            }
            val bilder = seiten
            if (bilder != null && bilder.isEmpty()) item { Text(stringResource(R.string.pdf_error)) }
            itemsIndexed(bilder.orEmpty()) { _, bmp ->
                Image(
                    bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth,
                )
            }
            // SR-9f0a44b25efd: zugeordnete Bescheide unter der Rechnung
            items(bescheide, key = { "bescheid_${it.id}" }) { b -> BescheidVorschau(container, b) }
        }
    }
}

/** SR-9f0a44b25efd: Überschrift mit Kasse und Datum, darunter die Seiten des Bescheid-Dokuments. */
@Composable
private fun BescheidVorschau(container: AppContainer, b: BescheidKopf) {
    val name = b.dokumentPfad?.takeIf { it.isNotBlank() }
    val seiten by produceState<List<Bitmap>?>(null, name) {
        if (name == null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                PdfWerkzeug.pdfSeiten(container.pdfDatei(name), 1080)
            } catch (e: Exception) {
                Log.e("Detail", "Bescheid nicht darstellbar: $name", e)
                emptyList()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.notice_heading, b.kasse.name, b.datum.format(RechnungEingabe.DatumFormat)),
            style = MaterialTheme.typography.titleSmall,
        )
        if (name == null) Text(stringResource(R.string.notice_document_missing))
        val bilder = seiten
        if (name != null && bilder != null && bilder.isEmpty()) Text(stringResource(R.string.notice_pdf_error))
        bilder.orEmpty().forEach { bmp ->
            Image(
                bmp.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth,
            )
        }
    }
}
