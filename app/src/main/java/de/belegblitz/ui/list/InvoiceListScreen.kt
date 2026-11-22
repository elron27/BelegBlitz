package de.belegblitz.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.belegblitz.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(onSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invoices_title)) },
                actions = { TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) } },
            )
        },
        floatingActionButton = {
            // Noch ohne Funktion (Schritt 1: Gerüst)
            ExtendedFloatingActionButton(onClick = {}) { Text(stringResource(R.string.new_invoice)) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.invoices_empty), textAlign = TextAlign.Center)
        }
    }
}