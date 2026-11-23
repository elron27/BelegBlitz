package de.belegblitz.ui.erfassung

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.R
import de.belegblitz.ui.ZurueckKnopf
import de.belegblitz.data.Rechnung
import de.belegblitz.domain.Geld
import de.belegblitz.domain.Kasse
import de.belegblitz.domain.RechnungEingabe

private fun Rechnung.zeile() = "$titel (${Geld.formatEuro(betragCent)})"

/**
 * SR-3273b2e9382d, SR-dbb8a31cd03b, SR-93cb9591c1a1, SR-a6fc7a6cdbe1, SR-e268ffbc5098:
 * Bescheid prüfen, Positionen Rechnungen zuordnen, Hinweis bearbeiten, speichern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BescheidPruefenScreen(erfassung: ErfassungViewModel, vm: BescheidViewModel, onZurueck: () -> Unit, onGespeichert: () -> Unit) {
    val ef by erfassung.state.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.auswerten(erfassung.state.value.seiten.aktuell) }
    // Dialog zur manuellen Zuordnung: Index der Position oder null
    var manuell by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notice_title)) },
                navigationIcon = { ZurueckKnopf(onClick = { vm.zuruecksetzen(); onZurueck() }) },
            )
        },
    ) { padding ->
        if (!s.geladen) {
            Text(stringResource(R.string.working), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.notice_hint), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Kasse.entries.forEach { k ->
                    FilterChip(selected = s.kasse == k, onClick = { vm.setKasse(k) }, label = { Text(k.name) })
                }
                if (s.kasseUnsicher) Text(stringResource(R.string.confirm_value), color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                s.datum, vm::setDatum, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.field_notice_date)) },
                isError = s.datumUnsicher, supportingText = if (s.datumUnsicher) { { Text(stringResource(R.string.confirm_value)) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            s.positionen.forEachIndexed { i, p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.position_n, i + 1), style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            p.datum, { vm.positionAendern(i, datum = it) }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.field_date)) }, isError = p.unsicher,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            p.fachrichtung, { vm.positionAendern(i, fachrichtung = it) }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.field_specialty)) },
                        )
                        OutlinedTextField(
                            p.rechnungsbetrag, { vm.positionAendern(i, rechnungsbetrag = it) }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.field_invoice_amount)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            p.erstattung, { vm.positionAendern(i, erstattung = it) }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.field_refund)) }, isError = p.unsicher,
                            supportingText = if (p.unsicher) { { Text(stringResource(R.string.confirm_value)) } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        // SR-dbb8a31cd03b Kriterium 3: Kandidaten zur Auswahl
                        val gewaehlt = s.rechnungen.firstOrNull { it.id == p.rechnungId }
                        if (p.kandidaten.isEmpty()) Text(stringResource(R.string.no_match), style = MaterialTheme.typography.bodySmall)
                        else if (p.kandidaten.size > 1 && gewaehlt == null) Text(stringResource(R.string.choose_invoice), style = MaterialTheme.typography.bodySmall)
                        p.kandidaten.mapNotNull { k -> s.rechnungen.firstOrNull { it.id == k.rechnungId } }.forEach { r ->
                            Row(Modifier.fillMaxWidth().clickable { vm.rechnungWaehlen(i, r.id) }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = r.id == p.rechnungId, onClick = { vm.rechnungWaehlen(i, r.id) })
                                Text(r.zeile(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (gewaehlt != null && p.kandidaten.none { it.rechnungId == gewaehlt.id }) {
                            Text(stringResource(R.string.assigned_to, gewaehlt.zeile()), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row {
                            // SR-dbb8a31cd03b Kriterium 4: von Hand zuordnen
                            TextButton(onClick = { manuell = i }) { Text(stringResource(R.string.assign_manually)) }
                            if (p.rechnungId != null) TextButton(onClick = { vm.rechnungWaehlen(i, null) }) { Text(stringResource(R.string.unassign)) }
                            TextButton(onClick = { vm.positionEntfernen(i) }) { Text(stringResource(R.string.remove)) }
                        }
                    }
                }
            }
            OutlinedButton(onClick = vm::positionHinzufuegen, Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_position)) }

            // SR-a6fc7a6cdbe1: Hinweiscodes und Erläuterung, bearbeitbar
            OutlinedTextField(
                s.hinweis, vm::setHinweis, Modifier.fillMaxWidth(), minLines = 2,
                label = { Text(stringResource(R.string.field_note)) },
            )
            if (s.fehler != null) Text(stringResource(s.fehler!!), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    vm.speichern(erfassung.state.value.seiten.aktuell) {
                        erfassung.entwurfLeeren()
                        onGespeichert()
                    }
                },
                enabled = !s.beschaeftigt && ef.seiten.aktuell.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    manuell?.let { idx ->
        AlertDialog(
            onDismissRequest = { manuell = null },
            title = { Text(stringResource(R.string.assign_manually)) },
            text = {
                LazyColumn {
                    items(s.rechnungen, key = { it.id }) { r ->
                        Text(
                            "${r.zeile()}\n${r.rechnungsdatum.format(RechnungEingabe.DatumFormat)}",
                            Modifier.fillMaxWidth().clickable { vm.rechnungWaehlen(idx, r.id); manuell = null }.padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { manuell = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
