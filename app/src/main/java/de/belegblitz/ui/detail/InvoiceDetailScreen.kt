package de.belegblitz.ui.detail

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.data.PdfWerkzeug
import de.belegblitz.domain.Geld
import de.belegblitz.domain.RechnungEingabe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SR-8d2f40b64013: Detailansicht zeigt die PDF-Seiten als Bilder (PdfRenderer). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(container: AppContainer, id: Long, onBack: () -> Unit) {
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rechnung?.titel.orEmpty()) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
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
            val bilder = seiten
            if (bilder != null && bilder.isEmpty()) item { Text(stringResource(R.string.pdf_error)) }
            itemsIndexed(bilder.orEmpty()) { _, bmp ->
                Image(
                    bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}
