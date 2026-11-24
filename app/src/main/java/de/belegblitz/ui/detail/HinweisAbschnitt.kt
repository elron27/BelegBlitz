package de.belegblitz.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.belegblitz.R

/**
 * Issue #17: Abschnitt „Hinweis“ für Rechnung und Bescheid. Tippen oder der Stift öffnen einen Dialog;
 * ein leer gespeicherter Text entfernt den Hinweis (Speichern mit null).
 */
@Composable
fun HinweisAbschnitt(hinweis: String?, onSpeichern: (String) -> Unit) {
    var dialog by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { dialog = true }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.remark_heading), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { dialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.remark_edit))
            }
        }
        if (hinweis.isNullOrBlank()) {
            Text(
                stringResource(R.string.remark_placeholder), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(hinweis, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (dialog) {
        var text by rememberSaveable(hinweis) { mutableStateOf(hinweis.orEmpty()) }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.remark_heading)) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.remark_label)) }, minLines = 3,
                )
            },
            confirmButton = { TextButton(onClick = { onSpeichern(text); dialog = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
