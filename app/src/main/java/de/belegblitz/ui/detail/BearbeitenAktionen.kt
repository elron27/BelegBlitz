package de.belegblitz.ui.detail

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.belegblitz.R

/** Issue #16: Stift (Bearbeiten) und Papierkorb (Löschen) als Aktionen der Titelleiste. */
@Composable
fun RowScope.BearbeitenLoeschenAktionen(onBearbeiten: () -> Unit, onLoeschen: () -> Unit) {
    IconButton(onClick = onBearbeiten) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit)) }
    IconButton(onClick = onLoeschen) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
}

/** Issue #16: Rückfrage vor dem Löschen; der Text weist darauf hin, dass es nicht rückgängig zu machen ist. */
@Composable
fun LoeschenDialog(titel: Int, text: Int, onBestaetigt: () -> Unit, onAbbruch: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAbbruch,
        title = { Text(stringResource(titel)) },
        text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = onBestaetigt) { Text(stringResource(R.string.delete_confirm)) } },
        dismissButton = { TextButton(onClick = onAbbruch) { Text(stringResource(R.string.cancel)) } },
    )
}
