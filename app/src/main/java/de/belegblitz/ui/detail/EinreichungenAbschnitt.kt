package de.belegblitz.ui.detail

import android.content.ComponentName
import android.content.Context
import de.belegblitz.MainActivity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.data.Einreichung
import de.belegblitz.data.Rechnung
import de.belegblitz.domain.EinreichungStatus
import de.belegblitz.domain.Einreichungsregeln
import de.belegblitz.domain.Geld
import de.belegblitz.domain.Kasse
import de.belegblitz.domain.RechnungEingabe
import kotlinx.coroutines.launch
import java.time.DateTimeException
import java.time.LocalDate

private fun statusText(s: EinreichungStatus) = when (s) {
    EinreichungStatus.ERFASST -> R.string.status_recorded
    EinreichungStatus.EINGEREICHT -> R.string.status_submitted
    EinreichungStatus.ERSTATTET -> R.string.status_refunded
    EinreichungStatus.TEILWEISE_ERSTATTET -> R.string.status_partly
    EinreichungStatus.ABGELEHNT -> R.string.status_rejected
}

/** SR-7d1f8913431f, SR-9b67f3ef174c, SR-a6fc7a6cdbe1, SR-d2f03abf6d13: Einreichung je Kasse in der Rechnungsansicht. */
@Composable
fun EinreichungenAbschnitt(container: AppContainer, rechnung: Rechnung) {
    val einreichungen by remember(rechnung.id) { container.db.einreichungDao().jeRechnung(rechnung.id) }
        .collectAsStateWithLifecycle(emptyList())
    val positionen by remember(rechnung.id) { container.db.bescheidDao().positionenJeRechnung(rechnung.id) }
        .collectAsStateWithLifecycle(emptyList())
    val dak = einreichungen.firstOrNull { it.kasse == Kasse.DAK }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.submissions), style = MaterialTheme.typography.titleMedium)
        Kasse.entries.forEach { kasse ->
            val e = einreichungen.firstOrNull { it.kasse == kasse }
            // SR-d2f03abf6d13: Rest der DAK als Vorschlag für die VKB
            val rest = if (kasse == Kasse.VKB && dak != null) {
                Einreichungsregeln.vkbRestbetrag(dak.status, dak.betragCent, dak.erstatteterBetragCent)
            } else null
            EinreichungKarte(container, rechnung, kasse, e, rest, positionen.filter { it.einreichungId == e?.id }.map { it.betragCent })
        }
    }
}

@Composable
private fun EinreichungKarte(
    container: AppContainer, rechnung: Rechnung, kasse: Kasse, e: Einreichung?, vkbRest: Long?, bescheidBetraege: List<Long>,
) {
    val scope = rememberCoroutineScope()
    val speicher = container.speicher
    fun tun(name: String, block: suspend () -> Unit) = scope.launch {
        try { block() } catch (ex: Exception) { Log.e("Einreichung", "$name fehlgeschlagen", ex) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(kasse.name, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(e?.let { statusText(it.status) } ?: R.string.status_none))
            }
            if (e == null) {
                if (vkbRest != null) {
                    Text(stringResource(R.string.vkb_rest_hint, Geld.formatEuro(vkbRest)), style = MaterialTheme.typography.bodyMedium)
                }
                val betrag = vkbRest ?: rechnung.betragCent
                OutlinedButton(
                    onClick = { tun("Anlegen") { speicher.anlegen(rechnung, kasse, betrag) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.create_submission, kasse.name, Geld.formatEuro(betrag))) }
                return@Column
            }
            BetragFeld(e) { c -> tun("Betrag") { speicher.betragAendern(e, c) } }
            e.erstatteterBetragCent?.let {
                Text(stringResource(R.string.refunded_amount, Geld.formatEuro(it)), style = MaterialTheme.typography.bodyMedium)
            }
            bescheidBetraege.forEach {
                Text(stringResource(R.string.notice_amount, Geld.formatEuro(it)), style = MaterialTheme.typography.bodySmall)
            }
            // SR-7d1f8913431f Kriterium 3: Verweis auf das eingereichte Dokument (die Seiten stehen unten)
            e.dokumentPfad?.let { name ->
                val context = LocalContext.current
                Text(stringResource(R.string.submitted_document, name), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { dokumentOeffnen(context, container.pdfDatei(name)) }) {
                    Text(stringResource(R.string.open_document))
                }
            }
            // SR-9b67f3ef174c: ein Tipp
            if (Einreichungsregeln.kannAlsEingereichtMarkiert(e.status)) {
                Button(
                    onClick = { tun("Eingereicht") { speicher.alsEingereichtMarkieren(e) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.mark_submitted)) }
            }
            if (e.einreichungsdatum != null) DatumFeld(e) { d -> tun("Datum") { speicher.datumAendern(e, d) } }
            HinweisFeld(e) { t -> tun("Hinweis") { speicher.hinweisAendern(e, t) } }
        }
    }
}

/** SR-7d1f8913431f Kriterium 3: eingereichtes Dokument im PDF-Betrachter öffnen. */
private fun dokumentOeffnen(context: Context, datei: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Die eigene App (PDF-Import per "Öffnen mit") soll nicht als Betrachter angeboten werden
        val auswahl = Intent.createChooser(intent, null).putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(context, MainActivity::class.java)),
        )
        context.startActivity(auswahl)
    } catch (ex: Exception) {
        Log.e("Einreichung", "Dokument nicht zu öffnen: ${datei.name}", ex)
        Toast.makeText(context, R.string.open_document_failed, Toast.LENGTH_LONG).show()
    }
}

/** SR-7d1f8913431f: Betrag der Einreichung bearbeiten. */
@Composable
private fun BetragFeld(e: Einreichung, speichern: (Long) -> Unit) {
    val original = "%d,%02d".format(e.betragCent / 100, e.betragCent % 100)
    var text by remember(original) { mutableStateOf(original) }
    val cent = Geld.parseCent(text)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            text, { text = it }, Modifier.weight(1f), singleLine = true, isError = cent == null || cent <= 0,
            label = { Text(stringResource(R.string.submission_amount_field)) },
        )
        OutlinedButton(onClick = { cent?.let(speichern) }, enabled = cent != null && cent > 0 && text != original) {
            Text(stringResource(R.string.save))
        }
    }
}

/** SR-9b67f3ef174c Kriterium 2: Einreichungsdatum ändern. */
@Composable
private fun DatumFeld(e: Einreichung, speichern: (LocalDate) -> Unit) {
    val original = e.einreichungsdatum?.format(RechnungEingabe.DatumFormat).orEmpty()
    var text by remember(original) { mutableStateOf(original) }
    val datum = try { LocalDate.parse(text.trim(), RechnungEingabe.DatumFormat) } catch (ex: DateTimeException) { null }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            text, { text = it }, Modifier.weight(1f), singleLine = true, isError = datum == null,
            label = { Text(stringResource(R.string.submitted_on)) },
        )
        OutlinedButton(onClick = { datum?.let(speichern) }, enabled = datum != null && text != original) {
            Text(stringResource(R.string.save))
        }
    }
}

/** SR-a6fc7a6cdbe1: Hinweis an der Einreichung anzeigen und bearbeiten. */
@Composable
private fun HinweisFeld(e: Einreichung, speichern: (String) -> Unit) {
    val original = e.hinweis.orEmpty()
    var text by remember(original) { mutableStateOf(original) }
    OutlinedTextField(
        text, { text = it }, Modifier.fillMaxWidth(), minLines = 2,
        label = { Text(stringResource(R.string.field_note)) },
    )
    if (text != original) OutlinedButton(onClick = { speichern(text) }) { Text(stringResource(R.string.save)) }
}
