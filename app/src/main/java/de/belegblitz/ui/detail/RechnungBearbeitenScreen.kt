package de.belegblitz.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.ui.ZurueckKnopf
import de.belegblitz.ui.erfassung.RechnungFormular
import kotlinx.coroutines.launch

/**
 * Issue #16: Rechnung bearbeiten (Praxis, Fachrichtung, Datum, Betrag) mit demselben Formular wie beim Erfassen.
 * Der Titel wird neu abgeleitet, der PDF-Name bleibt; Einreichungen ziehen bei Betragsänderung nach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechnungBearbeitenScreen(container: AppContainer, id: Long, onFertig: () -> Unit) {
    val rechnung by remember(id) { container.db.rechnungDao().finde(id) }.collectAsStateWithLifecycle(null)
    var praxis by rememberSaveable { mutableStateOf("") }
    var fachrichtung by rememberSaveable { mutableStateOf("") }
    var datum by rememberSaveable { mutableStateOf("") }
    var betrag by rememberSaveable { mutableStateOf("") }
    var geladen by rememberSaveable { mutableStateOf(false) }
    var ungueltig by rememberSaveable { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Startwerte einmalig aus der gespeicherten Rechnung übernehmen
    LaunchedEffect(rechnung?.id) {
        val r = rechnung ?: return@LaunchedEffect
        if (geladen) return@LaunchedEffect
        praxis = r.praxis; fachrichtung = r.fachrichtung
        datum = r.rechnungsdatum.format(RechnungEingabe.DatumFormat)
        betrag = "%d,%02d".format(r.betragCent / 100, r.betragCent % 100)
        geladen = true
    }
    val eingabe = RechnungEingabe.pruefen(praxis, fachrichtung, datum, betrag)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_invoice_title)) },
                navigationIcon = { ZurueckKnopf(onClick = onFertig) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RechnungFormular(
                praxis, fachrichtung, datum, betrag,
                onPraxis = { praxis = it }, onFachrichtung = { fachrichtung = it }, onDatum = { datum = it }, onBetrag = { betrag = it },
                ungueltig = ungueltig, onFeld = { ungueltig = false },
            )
            Button(
                onClick = {
                    val r = rechnung
                    if (eingabe == null || r == null) ungueltig = true
                    else scope.launch { container.speicher.rechnungAendern(r, eingabe); onFertig() }
                },
                enabled = geladen,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
