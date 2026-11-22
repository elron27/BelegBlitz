package de.belegblitz.ui.auswertung

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.SuchDokument
import de.belegblitz.domain.SuchTreffer
import de.belegblitz.domain.Volltextsuche

/** SR-84e9478a8eb5: lokale Volltextsuche über OCR-Text von Rechnungen und Bescheiden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SucheScreen(container: AppContainer, onBack: () -> Unit, onOeffnen: (Long) -> Unit) {
    val db = container.db
    val rechnungen by remember { db.rechnungDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val bescheide by remember { db.bescheidDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val positionen by remember { db.bescheidDao().allePositionen() }.collectAsStateWithLifecycle(emptyList())
    var anfrage by remember { mutableStateOf("") }

    val treffer by remember {
        derivedStateOf {
            val dokumente = rechnungen.map {
                SuchDokument(SuchDokument.Art.RECHNUNG, it.id, it.titel, it.ocrText, it.id)
            } + bescheide.map { b ->
                SuchDokument(
                    SuchDokument.Art.BESCHEID, b.id, "${b.kasse.name} ${b.datum.format(RechnungEingabe.DatumFormat)}",
                    b.ocrText, positionen.firstOrNull { it.bescheidId == b.id }?.rechnungId,
                )
            }
            Volltextsuche.suchen(dokumente, anfrage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                anfrage, { anfrage = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.search_hint)) },
            )
            if (anfrage.isNotBlank() && treffer.isEmpty()) Text(stringResource(R.string.search_none))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(treffer, key = { "${it.dokument.art}${it.dokument.id}" }) { t ->
                    val ziel = t.dokument.rechnungId
                    Card(Modifier.fillMaxWidth().let { if (ziel != null) it.clickable { onOeffnen(ziel) } else it }) {
                        Column(Modifier.padding(12.dp)) {
                            val art = if (t.dokument.art == SuchDokument.Art.BESCHEID) stringResource(R.string.search_notice) + ": " else ""
                            Text(art + t.dokument.titel, style = MaterialTheme.typography.titleSmall)
                            Text(hervorgehoben(t), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** Einfache Trefferhervorhebung: fett auf gelbem Grund. */
private fun hervorgehoben(t: SuchTreffer) = buildAnnotatedString {
    var pos = 0
    for (r in t.bereiche) {
        append(t.auszug.substring(pos, r.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = Color(0xFFFFF176), color = Color.Black)) {
            append(t.auszug.substring(r.first, r.last + 1))
        }
        pos = r.last + 1
    }
    append(t.auszug.substring(pos))
}
