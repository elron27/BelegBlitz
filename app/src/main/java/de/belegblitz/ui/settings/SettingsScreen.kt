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
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import de.belegblitz.BuildConfig
import de.belegblitz.R
import de.belegblitz.ui.ZurueckKnopf
import de.belegblitz.data.update.AppUpdater
import de.belegblitz.domain.Sperre
import kotlinx.coroutines.launch
import de.belegblitz.domain.Geld

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val beitraege by vm.beitraege.collectAsStateWithLifecycle()
    var betrag by remember { mutableStateOf("") }
    var datum by remember { mutableStateOf("") }
    var fehler by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sperreAktiv by vm.sperreAktiv.collectAsStateWithLifecycle()
    var sperreFehler by remember { mutableStateOf(false) }
    var wochenText by remember { mutableStateOf(vm.wartezeitWochen.value.toString()) }
    val updateOrdnerText by vm.updateOrdner.collectAsStateWithLifecycle()
    val updateOrdner = updateOrdnerText?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
    var updateResult by remember { mutableStateOf<AppUpdater.CheckResult?>(null) }
    var updateInstallError by remember { mutableStateOf<Int?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    val updatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            vm.setUpdateOrdner(uri.toString())
            updateResult = null
            updateInstallError = null
        }
    }

    // SR-e334c167571f / SR-176f6f1f60df: Export und Wiederherstellung per Ordnerauswahl
    val stand by vm.sicherungsStand.collectAsStateWithLifecycle()
    var wiederherstellenUri by remember { mutableStateOf<Uri?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.exportieren(uri)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            if (vm.bestandVorhanden()) wiederherstellenUri = uri else vm.wiederherstellen(uri)
        }
    }
    wiederherstellenUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { wiederherstellenUri = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { wiederherstellenUri = null; vm.wiederherstellen(uri) }) {
                    Text(stringResource(R.string.restore_confirm_yes))
                }
            },
            dismissButton = { TextButton(onClick = { wiederherstellenUri = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { ZurueckKnopf(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                // SR-c43e8d9ab0a8: Sperre ein-/ausschalten
                Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lock_switch), modifier = Modifier.weight(1f))
                    Switch(checked = sperreAktiv, onCheckedChange = { wunsch ->
                        val moeglich = BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
                            BiometricManager.BIOMETRIC_SUCCESS
                        if (!wunsch) { vm.setSperreAktiv(false); sperreFehler = false }
                        else if (Sperre.einschaltenMoeglich(moeglich)) { vm.setSperreAktiv(true); sperreFehler = false }
                        else sperreFehler = true
                    })
                }
                if (sperreFehler) Text(stringResource(R.string.lock_unavailable), color = MaterialTheme.colorScheme.error)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            item {
                // SR-aada699191a1: Wartezeit der Erinnerung
                Text(stringResource(R.string.reminder_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.reminder_info), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = wochenText,
                    onValueChange = { neu ->
                        wochenText = neu.filter { it.isDigit() }.take(2)
                        wochenText.toIntOrNull()?.let { vm.setWartezeitWochen(it) }
                    },
                    label = { Text(stringResource(R.string.reminder_weeks)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            item {
                // App-Update aus dem gewählten Ordner (ohne Netzwerk)
                Text(stringResource(R.string.settings_update), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_update_info), style = MaterialTheme.typography.bodySmall)
                Text(
                    updateOrdner?.let { Uri.decode(DocumentsContract.getTreeDocumentId(it)) }
                        ?: stringResource(R.string.settings_update_none),
                )
                TextButton(onClick = { updatePicker.launch(null) }) { Text(stringResource(R.string.settings_update_choose)) }
                Button(
                    enabled = !updateBusy && updateOrdner != null,
                    onClick = {
                        scope.launch {
                            updateBusy = true
                            updateInstallError = null
                            updateResult = vm.updater.check(updateOrdner)
                            updateBusy = false
                        }
                    },
                ) { Text(stringResource(R.string.settings_update_check)) }
                when (val r = updateResult) {
                    null -> Unit
                    is AppUpdater.CheckResult.UpToDate ->
                        Text(stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME))
                    is AppUpdater.CheckResult.Available -> {
                        Text(stringResource(R.string.update_available, r.release.versionName))
                        Button(
                            enabled = !updateBusy,
                            onClick = {
                                scope.launch {
                                    updateBusy = true
                                    updateInstallError = vm.updater.download(r.release, r.folder).fold(
                                        onSuccess = { file -> vm.updater.install(file) },
                                        onFailure = { e -> (e as? AppUpdater.UpdateException)?.messageRes ?: R.string.update_error_read },
                                    )
                                    updateBusy = false
                                }
                            },
                        ) { Text(stringResource(R.string.update_install)) }
                    }
                    is AppUpdater.CheckResult.Failed -> Text(
                        stringResource(r.message) + (r.detail?.let { " ($it)" } ?: ""),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                updateInstallError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            item {
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backup_info), style = MaterialTheme.typography.bodySmall)
                val laeuft = stand is SettingsViewModel.SicherungsStand.Laeuft
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !laeuft, onClick = { exportPicker.launch(null) }) { Text(stringResource(R.string.backup_export)) }
                    Button(enabled = !laeuft, onClick = { restorePicker.launch(null) }) { Text(stringResource(R.string.backup_restore)) }
                }
                when (val st = stand) {
                    SettingsViewModel.SicherungsStand.Leerlauf -> Unit
                    is SettingsViewModel.SicherungsStand.Laeuft -> {
                        Text(stringResource(if (st.export) R.string.backup_running_export else R.string.backup_running_restore, st.erledigt, st.gesamt))
                        LinearProgressIndicator(progress = { st.erledigt.toFloat() / st.gesamt.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                    }
                    is SettingsViewModel.SicherungsStand.Fertig -> {
                        val e = st.ergebnis
                        Text(stringResource(if (st.export) R.string.backup_done_export else R.string.backup_done_restore, e.rechnungen, e.bescheide, e.dokumente))
                        if (e.fehlendeDokumente > 0) Text(stringResource(R.string.backup_missing, e.fehlendeDokumente), color = MaterialTheme.colorScheme.error)
                    }
                    is SettingsViewModel.SicherungsStand.Fehler -> Text(
                        stringResource(if (st.export) R.string.backup_error_export else R.string.backup_error_restore, st.meldung ?: "-"),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
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