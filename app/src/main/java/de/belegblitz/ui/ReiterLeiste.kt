package de.belegblitz.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import de.belegblitz.R
import de.belegblitz.domain.Reiter

/**
 * SR-1571303342c4 (löst SR-1d598be18236 Kriterium 1 ab): feste untere Leiste mit den vier Hauptzielen
 * (ohne Icons-Bibliothek, nur Text); [onWaehlen] meldet das gewählte Ziel, auch das bereits aktive.
 */
@Composable
fun ReiterLeiste(aktiv: Reiter, onWaehlen: (Reiter) -> Unit) {
    NavigationBar {
        for (reiter in Reiter.entries) {
            NavigationBarItem(
                selected = aktiv == reiter, onClick = { if (reiter != aktiv) onWaehlen(reiter) },
                icon = {}, alwaysShowLabel = true,
                label = { Text(stringResource(reiter.beschriftung()), maxLines = 1) },
            )
        }
    }
}

private fun Reiter.beschriftung() = when (this) {
    Reiter.RECHNUNGEN -> R.string.invoices_title
    Reiter.BESCHEIDE -> R.string.tab_notices
    Reiter.UEBERSICHT -> R.string.overview_title
    Reiter.SUCHE -> R.string.search_title
}

/** SR-1571303342c4 Kriterium 4: einheitliches „⋮“-Menü der Hauptansichten, enthält nur Einstellungen. */
@Composable
fun UeberlaufMenue(onSettings: () -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val beschreibung = stringResource(R.string.more_options)
    IconButton(
        onClick = { offen = true },
        modifier = Modifier.size(TitelleistenKnopfGroesse).semantics { contentDescription = beschreibung },
    ) {
        Text("⋮", fontSize = 24.sp)
    }
    DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { offen = false; onSettings() })
    }
}
