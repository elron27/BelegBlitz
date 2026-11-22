package de.belegblitz.ui.erfassung

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Kleine Vorschau einer Seitendatei. */
@Composable
fun Vorschaubild(pfad: String, modifier: Modifier = Modifier) {
    val bild by produceState<Bitmap?>(null, pfad) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(pfad, BitmapFactory.Options().apply { inSampleSize = 4 })
        }
    }
    bild?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier) }
}

/** SR-8d2f40b64013 / SR-4eb0053fe161 / SR-166e5ad565ff: Seiten aufnehmen, importieren, ordnen, lösen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErfassungScreen(vm: ErfassungViewModel, onWeiter: () -> Unit, onAbbrechen: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val seiten = state.seiten
    val snackbar = remember { SnackbarHostState() }

    // Zieldatei der laufenden Kameraaufnahme
    val ziel = remember { arrayOfNulls<File>(1) }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { erfolg ->
        ziel[0]?.let { vm.fotoAufgenommen(it, erfolg) }
        ziel[0] = null
    }
    val dateiAuswahl = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pdfImportieren(uri)
    }

    val fehlerText = state.fehler?.let { stringResource(it) }
    LaunchedEffect(fehlerText) {
        if (fehlerText != null) { snackbar.showSnackbar(fehlerText); vm.fehlerQuittiert() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    TextButton(onClick = { vm.verwerfen(); onAbbrechen() }) { Text(stringResource(R.string.cancel)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val (datei, uri) = vm.neueFotoDatei()
                            ziel[0] = datei
                            kamera.launch(uri)
                        },
                        enabled = !state.beschaeftigt,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.take_photo)) }
                    OutlinedButton(
                        onClick = { dateiAuswahl.launch(arrayOf("application/pdf")) },
                        enabled = !state.beschaeftigt,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.import_pdf)) }
                }
                Button(
                    onClick = onWeiter,
                    enabled = seiten.aktuell.isNotEmpty() && !state.beschaeftigt,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.next)) }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.beschaeftigt) item { Text(stringResource(R.string.working)) }
            if (seiten.aktuell.isEmpty()) item {
                Text(stringResource(R.string.capture_hint), style = MaterialTheme.typography.bodyMedium)
            }
            itemsIndexed(seiten.aktuell, key = { _, p -> p }) { i, pfad ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Vorschaubild(pfad, Modifier.size(width = 72.dp, height = 96.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(stringResource(R.string.page_n, i + 1), style = MaterialTheme.typography.titleSmall)
                            Row {
                                TextButton(onClick = { vm.verschieben(i, i - 1) }, enabled = i > 0) {
                                    Text(stringResource(R.string.move_up))
                                }
                                TextButton(onClick = { vm.verschieben(i, i + 1) }, enabled = i < seiten.aktuell.lastIndex) {
                                    Text(stringResource(R.string.move_down))
                                }
                            }
                            Row {
                                TextButton(onClick = { vm.loesen(i) }) { Text(stringResource(R.string.detach)) }
                                TextButton(onClick = { vm.entfernen(i) }) { Text(stringResource(R.string.remove)) }
                            }
                        }
                    }
                }
            }
            if (seiten.geloest.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.detached_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.detached_hint), style = MaterialTheme.typography.bodySmall)
                }
                itemsIndexed(seiten.geloest, key = { _, p -> p }) { i, pfad ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Vorschaubild(pfad, Modifier.size(width = 48.dp, height = 64.dp))
                            Text(stringResource(R.string.detached_page), Modifier.weight(1f).padding(start = 12.dp))
                            TextButton(onClick = { vm.zurueckholen(i) }) { Text(stringResource(R.string.merge_back)) }
                        }
                    }
                }
            }
            item { Column(Modifier.height(8.dp)) {} }
        }
    }
}
