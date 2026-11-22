package de.belegblitz

import android.content.Context
import android.net.Uri
import androidx.room.Room
import de.belegblitz.data.AppDatabase
import de.belegblitz.data.Person
import de.belegblitz.domain.StandardPerson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import de.belegblitz.domain.KeinErkenner
import de.belegblitz.domain.RechnungErkenner
import java.io.File

/** Manuelle Abhängigkeitsverwaltung (kein Hilt). */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "belegblitz.db").build()

    /** SR-619cbca1ae6e: hier steckt Schritt 3 die OCR-Erkennung ein. */
    var erkenner: RechnungErkenner = KeinErkenner

    /** SR-4eb0053fe161: per Teilen/Öffnen eingegangenes PDF, bis die Navigation es übernimmt. */
    val eingehendesPdf = MutableStateFlow<Uri?>(null)

    /** Zwischenspeicher für Seitenbilder der Rechnung, die gerade erfasst wird. */
    val entwurfOrdner: File get() = File(context.filesDir, "entwurf").apply { mkdirs() }

    /** SR-8d2f40b64013: fertige PDFs liegen im app-internen Speicher. */
    val pdfOrdner: File get() = File(context.filesDir, "rechnungen").apply { mkdirs() }

    fun pdfDatei(name: String): File = File(pdfOrdner, name)

    init {
        // SR-4e84512d52d0: beim ersten Start genau eine Person anlegen
        scope.launch {
            if (StandardPerson.mussAngelegtWerden(db.personDao().anzahl())) {
                db.personDao().einfuegen(Person(name = context.getString(R.string.default_person_name)))
            }
        }
    }
}