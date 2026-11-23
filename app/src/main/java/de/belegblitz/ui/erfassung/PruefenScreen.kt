package de.belegblitz.ui.erfassung

import androidx.compose.foundation.layout.Arrangement
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
@OptIn(ExperimentalMaterial3Api::class)
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
    fun markierung(f: VorschlagFeld): @Composable (() -> Unit)? =
        if (f in offen) { { Text(stringResource(R.string.confirm_value)) } } else null

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
            OutlinedTextField(
                praxis, { praxis = it; bestaetige(VorschlagFeld.PRAXIS) }, Modifier.fillMaxWidth(), singleLine = true,
                isError = VorschlagFeld.PRAXIS in offen, supportingText = markierung(VorschlagFeld.PRAXIS),
                label = { Text(stringResource(R.string.field_practice)) },
            )
            OutlinedTextField(
                fachrichtung, { fachrichtung = it; bestaetige(VorschlagFeld.FACHRICHTUNG) }, Modifier.fillMaxWidth(), singleLine = true,
                isError = VorschlagFeld.FACHRICHTUNG in offen, supportingText = markierung(VorschlagFeld.FACHRICHTUNG),
                label = { Text(stringResource(R.string.field_specialty)) },
            )
            OutlinedTextField(
                datum, { datum = it; bestaetige(VorschlagFeld.DATUM) }, Modifier.fillMaxWidth(), singleLine = true,
                isError = VorschlagFeld.DATUM in offen, supportingText = markierung(VorschlagFeld.DATUM),
                label = { Text(stringResource(R.string.field_date)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                betrag, { betrag = it; bestaetige(VorschlagFeld.BETRAG) }, Modifier.fillMaxWidth(), singleLine = true,
                isError = VorschlagFeld.BETRAG in offen, supportingText = markierung(VorschlagFeld.BETRAG),
                label = { Text(stringResource(R.string.field_amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (eingabe != null) Text(stringResource(R.string.title_preview, eingabe.titel), style = MaterialTheme.typography.bodySmall)
            if (ungueltig) Text(stringResource(R.string.review_invalid), color = MaterialTheme.colorScheme.error)
            state.fehler?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
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
