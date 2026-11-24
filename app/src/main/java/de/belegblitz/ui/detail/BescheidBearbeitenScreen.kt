package de.belegblitz.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.domain.Geld
import de.belegblitz.domain.Kasse
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.Titel
import de.belegblitz.ui.ZurueckKnopf
import de.belegblitz.ui.erfassung.DatumDialog
import kotlinx.coroutines.launch
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Issue #16: Bescheid bearbeiten: Kasse, Datum, Hinweis, Beträge der Positionen ändern und Positionen entfernen
 * (neue Positionen gibt es nicht). Die Erstattungen der betroffenen Einreichungen werden neu berechnet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BescheidBearbeitenScreen(container: AppContainer, id: Long, onFertig: () -> Unit) {
    val db = container.db
    val bescheid by remember(id) { db.bescheidDao().finde(id) }.collectAsStateWithLifecycle(null)
    val positionen by remember(id) { db.bescheidDao().positionenJeBescheid(id) }.collectAsStateWithLifecycle(emptyList())
    val rechnungen by remember { db.rechnungDao().alle() }.collectAsStateWithLifecycle(emptyList())
    val titelJeId = rechnungen.associate { it.id to it.titel }
    var kasse by rememberSaveable { mutableStateOf(Kasse.DAK.name) }
    var datum by rememberSaveable { mutableStateOf("") }
    var hinweis by rememberSaveable { mutableStateOf("") }
    var geladen by rememberSaveable { mutableStateOf(false) }
    var ungueltig by rememberSaveable { mutableStateOf(false) }
    var kalender by rememberSaveable { mutableStateOf(false) }
    val betraege = remember { mutableStateMapOf<Long, String>() }
    val entfernt = remember { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()
    // Startwerte einmalig aus dem gespeicherten Bescheid übernehmen
    LaunchedEffect(bescheid?.id, positionen.size) {
        val b = bescheid ?: return@LaunchedEffect
        if (!geladen) {
            kasse = b.kasse.name
            datum = b.datum.format(RechnungEingabe.DatumFormat)
            hinweis = b.hinweis.orEmpty()
            geladen = true
        }
        positionen.forEach { p -> if (p.id !in betraege) betraege[p.id] = "%d,%02d".format(p.betragCent / 100, p.betragCent % 100) }
    }
    val sichtbar = positionen.filter { it.id !in entfernt }
    val datumWert = try { LocalDate.parse(datum.trim(), RechnungEingabe.DatumFormat) } catch (e: DateTimeException) { null }
    val betragWerte = sichtbar.associate { it.id to Geld.parseCent(betraege[it.id].orEmpty()) }
    val gueltig = geladen && datumWert != null && betragWerte.values.all { it != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_notice_title)) },
                navigationIcon = { ZurueckKnopf(onClick = onFertig) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Kasse.entries.forEach { k ->
                    FilterChip(selected = kasse == k.name, onClick = { kasse = k.name }, label = { Text(k.name) })
                }
            }
            OutlinedTextField(
                datum, { datum = it; ungueltig = false }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.field_notice_date)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    IconButton(onClick = { kalender = true }) { Icon(Icons.Filled.CalendarMonth, stringResource(R.string.pick_date)) }
                },
            )
            if (kalender) DatumDialog(datum, { datum = it; ungueltig = false; kalender = false }, { kalender = false })
            OutlinedTextField(
                hinweis, { hinweis = it }, Modifier.fillMaxWidth(), minLines = 2,
                label = { Text(stringResource(R.string.remark_label)) },
            )
            Text(stringResource(R.string.edit_notice_positions), style = MaterialTheme.typography.titleSmall)
            sichtbar.forEach { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            titelJeId[p.rechnungId]?.let { Titel.fuerAnzeige(it) } ?: "#${p.rechnungId}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            betraege[p.id].orEmpty(), { betraege[p.id] = it; ungueltig = false }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.field_refund)) },
                            isError = betragWerte[p.id] == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        TextButton(onClick = { entfernt += p.id }) { Text(stringResource(R.string.remove)) }
                    }
                }
            }
            if (ungueltig) Text(stringResource(R.string.edit_notice_invalid), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    if (!gueltig || datumWert == null) { ungueltig = true; return@Button }
                    scope.launch {
                        container.speicher.bescheidAendern(
                            id, Kasse.valueOf(kasse), datumWert, hinweis,
                            betragWerte.mapNotNull { (k, v) -> v?.let { k to it } }.toMap(), entfernt.toSet(),
                        )
                        onFertig()
                    }
                },
                enabled = geladen,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
