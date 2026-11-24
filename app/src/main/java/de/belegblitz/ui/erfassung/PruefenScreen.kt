package de.belegblitz.ui.erfassung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.R
import de.belegblitz.ui.ZurueckKnopf
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.Unsicherheit
import de.belegblitz.domain.VorschlagFeld

/** SR-619cbca1ae6e: Prüfbildschirm mit Praxis, Fachrichtung, Datum, Betrag; Felder kommen leer oder vom Erkenner. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PruefenScreen(vm: ErfassungViewModel, onZurueck: () -> Unit, onGespeichert: (weitereSeiten: Boolean) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var praxis by rememberSaveable { mutableStateOf("") }
    var fachrichtung by rememberSaveable { mutableStateOf("") }
    var datum by rememberSaveable { mutableStateOf("") }
    var betrag by rememberSaveable { mutableStateOf("") }
    var ungueltig by rememberSaveable { mutableStateOf(false) }
    // SR-e268ffbc5098: unsicher erkannte Felder bleiben markiert, bis der Nutzer sie anfasst (bestätigt/korrigiert)
    var bestaetigtText by rememberSaveable { mutableStateOf("") }
    val bestaetigt = bestaetigtText.split(',').mapNotNull { n -> VorschlagFeld.entries.firstOrNull { it.name == n } }.toSet()
    val offen = Unsicherheit.offen(state.vorschlag, bestaetigt)
    fun bestaetige(f: VorschlagFeld) { bestaetigtText = (bestaetigt + f).joinToString(",") { it.name }; ungueltig = false }

    LaunchedEffect(Unit) { if (state.vorschlag == null) vm.vorschlagLaden() }
    // Vorschlag nur in leere Felder eintragen, damit Eingaben des Nutzers nie überschrieben werden
    LaunchedEffect(state.vorschlag) {
        val v = state.vorschlag ?: return@LaunchedEffect
        if (praxis.isEmpty()) praxis = v.praxis.orEmpty()
        if (fachrichtung.isEmpty()) fachrichtung = v.fachrichtung.orEmpty()
        if (datum.isEmpty()) datum = v.datum?.format(RechnungEingabe.DatumFormat).orEmpty()
        if (betrag.isEmpty()) betrag = v.betragCent?.let { "%d,%02d".format(it / 100, it % 100) }.orEmpty()
    }

    val eingabe = RechnungEingabe.pruefen(praxis, fachrichtung, datum, betrag)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = { ZurueckKnopf(onClick = onZurueck) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.review_hint), style = MaterialTheme.typography.bodyMedium)
            RechnungFormular(
                praxis, fachrichtung, datum, betrag,
                onPraxis = { praxis = it }, onFachrichtung = { fachrichtung = it }, onDatum = { datum = it }, onBetrag = { betrag = it },
                offen = offen, datumKandidaten = state.vorschlag?.datumKandidaten.orEmpty(),
                ungueltig = ungueltig, fehler = state.fehler,
                onFeld = ::bestaetige,
            )
            Button(
                onClick = {
                    if (eingabe == null) ungueltig = true else vm.speichern(eingabe) { weitere ->
                        praxis = ""; fachrichtung = ""; datum = ""; betrag = ""; bestaetigtText = ""
                        onGespeichert(weitere)
                    }
                },
                enabled = !state.beschaeftigt,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
