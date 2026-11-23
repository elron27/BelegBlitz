package de.belegblitz.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import de.belegblitz.R

/** Mindestgröße der Trefferfläche für Knöpfe in der Titelleiste. */
val TitelleistenKnopfGroesse = 48.dp

/** SR-daf053d403cc (SR-d403cc): gemeinsamer Zurück-Knopf als Pfeil-Symbol, mindestens 48 dp, mit Beschreibungstext. */
@Composable
fun ZurueckKnopf(onClick: () -> Unit) {
    val beschreibung = stringResource(R.string.back)
    // material-icons-core ist nicht eingebunden; Pfeil als Text, damit keine neue Abhängigkeit nötig ist.
    IconButton(onClick = onClick, modifier = Modifier.size(TitelleistenKnopfGroesse).semantics { contentDescription = beschreibung }) {
        Text("←", fontSize = 24.sp)
    }
}
