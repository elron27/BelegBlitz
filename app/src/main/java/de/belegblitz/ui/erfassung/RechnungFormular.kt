package de.belegblitz.ui.erfassung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.belegblitz.R
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.VorschlagFeld
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * SR-619cbca1ae6e, Issue #16: Formularfelder einer Rechnung (Praxis, Fachrichtung, Datum, Betrag) mit Titel-Vorschau,
 * Kalenderauswahl und optionalen Datumsvorschlägen. Gemeinsam für Erfassen (Prüfen) und Bearbeiten.
 * Zustand und Speichern-Knopf liegen beim Aufrufer.
 * @param offen unsicher erkannte, noch unbestätigte Felder (nur beim Erfassen)
 * @param onFeld wird bei jeder Eingabe des Nutzers in ein Feld aufgerufen (Bestätigung unsicherer Felder)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RechnungFormular(
    praxis: String, fachrichtung: String, datum: String, betrag: String,
    onPraxis: (String) -> Unit, onFachrichtung: (String) -> Unit, onDatum: (String) -> Unit, onBetrag: (String) -> Unit,
    modifier: Modifier = Modifier,
    offen: Set<VorschlagFeld> = emptySet(),
    datumKandidaten: List<LocalDate> = emptyList(),
    ungueltig: Boolean = false,
    fehler: Int? = null,
    onFeld: (VorschlagFeld) -> Unit = {},
) {
    var kalender by rememberSaveable { mutableStateOf(false) }
    fun markierung(f: VorschlagFeld): @Composable (() -> Unit)? =
        if (f in offen) { { Text(stringResource(R.string.confirm_value)) } } else null
    val eingabe = RechnungEingabe.pruefen(praxis, fachrichtung, datum, betrag)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            praxis, { onPraxis(it); onFeld(VorschlagFeld.PRAXIS) }, Modifier.fillMaxWidth(), singleLine = true,
            isError = VorschlagFeld.PRAXIS in offen, supportingText = markierung(VorschlagFeld.PRAXIS),
            label = { Text(stringResource(R.string.field_practice)) },
        )
        OutlinedTextField(
            fachrichtung, { onFachrichtung(it); onFeld(VorschlagFeld.FACHRICHTUNG) }, Modifier.fillMaxWidth(), singleLine = true,
            isError = VorschlagFeld.FACHRICHTUNG in offen, supportingText = markierung(VorschlagFeld.FACHRICHTUNG),
            label = { Text(stringResource(R.string.field_specialty)) },
        )
        OutlinedTextField(
            datum, { onDatum(it); onFeld(VorschlagFeld.DATUM) }, Modifier.fillMaxWidth(), singleLine = true,
            isError = VorschlagFeld.DATUM in offen, supportingText = markierung(VorschlagFeld.DATUM),
            label = { Text(stringResource(R.string.field_date)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                IconButton(onClick = { kalender = true }) {
                    Icon(Icons.Filled.CalendarMonth, stringResource(R.string.pick_date))
                }
            },
        )
        // SR-306dc2: mehrere erkannte Daten als wählbare Vorschläge; Antippen setzt das Feld
        if (datumKandidaten.size > 1) {
            Text(stringResource(R.string.detected_dates), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in datumKandidaten) {
                    val text = k.format(RechnungEingabe.DatumFormat)
                    FilterChip(selected = datum.trim() == text, onClick = { onDatum(text); onFeld(VorschlagFeld.DATUM) }, label = { Text(text) })
                }
            }
        }
        OutlinedTextField(
            betrag, { onBetrag(it); onFeld(VorschlagFeld.BETRAG) }, Modifier.fillMaxWidth(), singleLine = true,
            isError = VorschlagFeld.BETRAG in offen, supportingText = markierung(VorschlagFeld.BETRAG),
            label = { Text(stringResource(R.string.field_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (kalender) DatumDialog(datum, { onDatum(it); onFeld(VorschlagFeld.DATUM); kalender = false }, { kalender = false })
        if (eingabe != null) Text(stringResource(R.string.title_preview, eingabe.titel), style = MaterialTheme.typography.bodySmall)
        if (ungueltig) Text(stringResource(R.string.review_invalid), color = MaterialTheme.colorScheme.error)
        fehler?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}

/** SR-306dc2: Kalenderauswahl; Startwert = aktuelles Feld (falls gültig), Ergebnis im Format [RechnungEingabe.DatumFormat]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatumDialog(aktuell: String, onWahl: (String) -> Unit, onAbbruch: () -> Unit) {
    val start = try { LocalDate.parse(aktuell.trim(), RechnungEingabe.DatumFormat) } catch (e: java.time.DateTimeException) { null }
    val state = rememberDatePickerState(initialSelectedDateMillis = start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onAbbruch,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onWahl(datumText(it)) } },
            ) { Text(stringResource(R.string.date_ok)) }
        },
        dismissButton = { TextButton(onClick = onAbbruch) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}

/** SR-306dc2: Auswahl des DatePickers (UTC-Mitternacht in Millisekunden) als Text im Feldformat. */
internal fun datumText(utcMillis: Long): String =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().format(RechnungEingabe.DatumFormat)
