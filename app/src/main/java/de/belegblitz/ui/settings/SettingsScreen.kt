package de.belegblitz.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
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
import de.belegblitz.domain.Geld

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val beitraege by vm.beitraege.collectAsStateWithLifecycle()
    var betrag by remember { mutableStateOf("") }
    var datum by remember { mutableStateOf("") }
    var fehler by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(R.string.vkb_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.vkb_hint), style = MaterialTheme.typography.bodySmall)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = betrag, onValueChange = { betrag = it; fehler = false },
                        label = { Text(stringResource(R.string.vkb_amount)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = datum, onValueChange = { datum = it; fehler = false },
                        label = { Text(stringResource(R.string.vkb_valid_from)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (fehler) Text(stringResource(R.string.vkb_invalid), color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        if (vm.hinzufuegen(betrag, datum)) { betrag = ""; datum = "" } else fehler = true
                    }) { Text(stringResource(R.string.vkb_add)) }
                    HorizontalDivider()
                }
            }
            if (beitraege.isEmpty()) item { Text(stringResource(R.string.vkb_empty)) }
            items(beitraege, key = { it.id }) { b ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.vkb_entry, Geld.formatEuro(b.monatsbetragCent), b.gueltigAb.format(DatumFormat)),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.loeschen(b) }) { Text(stringResource(R.string.vkb_delete)) }
                }
            }
        }
    }
}