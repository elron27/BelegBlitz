package de.belegblitz.ui

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.belegblitz.R
import de.belegblitz.domain.Reiter

/** SR-1d598be18236: untere Reiterleiste Rechnungen / Bescheide (ohne Icons-Bibliothek, nur Text). */
@Composable
fun ReiterLeiste(aktiv: Reiter, onRechnungen: () -> Unit, onBescheide: () -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = aktiv == Reiter.RECHNUNGEN, onClick = onRechnungen,
            icon = {}, alwaysShowLabel = true, label = { Text(stringResource(R.string.invoices_title)) },
        )
        NavigationBarItem(
            selected = aktiv == Reiter.BESCHEIDE, onClick = onBescheide,
            icon = {}, alwaysShowLabel = true, label = { Text(stringResource(R.string.tab_notices)) },
        )
    }
}
