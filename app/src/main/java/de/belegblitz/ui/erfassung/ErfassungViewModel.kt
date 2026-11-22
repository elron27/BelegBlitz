package de.belegblitz.ui.erfassung

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.data.PdfWerkzeug
import de.belegblitz.data.Rechnung
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.RechnungVorschlag
import de.belegblitz.domain.Seitengruppierung
import de.belegblitz.domain.SeitenMerkmal
import de.belegblitz.domain.Seitenstapel
import de.belegblitz.domain.nachGruppen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** SR-3273b2e9382d: Der Nutzer wählt, ob die erfassten Seiten eine Rechnung oder ein Bescheid sind. */
enum class BelegArt { RECHNUNG, BESCHEID }

data class ErfassungState(
    val art: BelegArt = BelegArt.RECHNUNG,
    val seiten: Seitenstapel<String> = Seitenstapel(),
    val beschaeftigt: Boolean = false,
    /** String-Ressource der letzten Fehlermeldung. */
    val fehler: Int? = null,
    /** SR-619cbca1ae6e: Vorschlag des Erkenners für den Prüfbildschirm. */
    val vorschlag: RechnungVorschlag? = null,
)

/** SR-8d2f40b64013, SR-4eb0053fe161, SR-166e5ad565ff, SR-619cbca1ae6e: gemeinsamer Zustand von Erfassen und Prüfen. */
class ErfassungViewModel(private val container: AppContainer, private val context: Context) : ViewModel() {
    private val _state = MutableStateFlow(ErfassungState())
    val state: StateFlow<ErfassungState> = _state

    /** Neue Zieldatei für die Kamera samt Uri für den FileProvider. */
    fun neueFotoDatei(): Pair<File, Uri> {
        val datei = File(container.entwurfOrdner, "foto_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei)
        return datei to uri
    }

    fun fotoAufgenommen(datei: File, erfolg: Boolean) {
        if (!erfolg || !datei.exists() || datei.length() == 0L) { datei.delete(); return }
        arbeiten {
            PdfWerkzeug.fotoAufbereiten(datei)
            _state.update { it.copy(seiten = it.seiten.hinzufuegen(datei.path)) }
        }
    }

    /** SR-4eb0053fe161: PDF (Teilen, Öffnen oder Dateiauswahl) wird in Seitenbilder zerlegt wie Fotos. */
    fun pdfImportieren(uri: Uri) {
        arbeiten {
            // SR-3273b2e9382d: Screenshots/Bilder (auch per Teilen) wie Fotos übernehmen
            if (context.contentResolver.getType(uri)?.startsWith("image/") == true) {
                val bild = File(container.entwurfOrdner, "bild_${UUID.randomUUID()}.jpg")
                val eingang = context.contentResolver.openInputStream(uri) ?: error("Datei nicht lesbar: $uri")
                eingang.use { i -> bild.outputStream().use { o -> i.copyTo(o) } }
                PdfWerkzeug.fotoAufbereiten(bild)
                _state.update { it.copy(seiten = it.seiten.hinzufuegen(bild.path)) }
                return@arbeiten
            }
            val temp = File(context.cacheDir, "import_${UUID.randomUUID()}.pdf")
            try {
                val eingang = context.contentResolver.openInputStream(uri) ?: error("Datei nicht lesbar: $uri")
                eingang.use { i -> temp.outputStream().use { o -> i.copyTo(o) } }
                val bilder = PdfWerkzeug.pdfZuBildern(temp, container.entwurfOrdner)
                _state.update { it.copy(seiten = it.seiten.hinzufuegen(bilder)) }
            } finally {
                temp.delete()
            }
        }
    }

    fun artWaehlen(art: BelegArt) = _state.update { it.copy(art = art) }

    /** Nach dem Speichern eines Bescheids: Zwischenbilder verwerfen (PDF liegt schon im Ablageordner). */
    fun entwurfLeeren() { _state.value = ErfassungState() }

    fun verschieben(von: Int, nach: Int) = aendern { it.verschieben(von, nach) }
    fun loesen(index: Int) = aendern { it.loesen(index) }
    fun zurueckholen(index: Int) = aendern { it.zurueckholen(index) }

    fun entfernen(index: Int) {
        val pfad = _state.value.seiten.aktuell.getOrNull(index) ?: return
        aendern { it.entfernen(index) }
        File(pfad).delete()
    }

    fun fehlerQuittiert() = _state.update { it.copy(fehler = null) }

    /** Verwirft den ganzen Entwurf samt Zwischenbildern. */
    fun verwerfen() {
        val s = _state.value.seiten
        (s.aktuell + s.geloest).forEach { File(it).delete() }
        _state.value = ErfassungState()
    }

    /** Fragt den Erkenner (derzeit Platzhalter) nach einem Vorschlag für die Felder. */
    fun vorschlagLaden() {
        viewModelScope.launch {
            val v = try {
                withContext(Dispatchers.IO) {
                    // SR-9d64ee2af904: Seiten verschiedener Rechnungen automatisch trennen
                    val alle = _state.value.seiten.aktuell
                    if (alle.size > 1) {
                        val einzeln = alle.map { container.erkenner.erkennen(listOf(it)) }
                        val gruppen = Seitengruppierung.gruppieren(einzeln.map { SeitenMerkmal(it.praxis, it.datum) })
                        if (gruppen.size > 1) _state.update { it.copy(seiten = it.seiten.nachGruppen(gruppen)) }
                    }
                    container.erkenner.erkennen(_state.value.seiten.aktuell)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erkennung fehlgeschlagen", e)
                RechnungVorschlag.LEER
            }
            _state.update { it.copy(vorschlag = v) }
        }
    }

    /** Erzeugt das PDF, legt die Rechnung an; `fertig(weitereSeiten)` meldet, ob gelöste Seiten übrig sind. */
    fun speichern(eingabe: RechnungEingabe, fertig: (weitereSeiten: Boolean) -> Unit) {
        val seiten = _state.value.seiten
        val ocrText = _state.value.vorschlag?.ocrText // SR-84e9478a8eb5: OCR-Text mit der Rechnung speichern
        if (seiten.aktuell.isEmpty() || _state.value.beschaeftigt) return
        _state.update { it.copy(beschaeftigt = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val name = "${eingabe.titel}_${UUID.randomUUID().toString().take(8)}.pdf"
                    PdfWerkzeug.bilderZuPdf(seiten.aktuell, container.pdfDatei(name))
                    val personId = container.db.personDao().ersteId() ?: error("Keine Person vorhanden")
                    container.db.rechnungDao().einfuegen(
                        Rechnung(
                            personId = personId, praxis = eingabe.praxis, fachrichtung = eingabe.fachrichtung,
                            rechnungsdatum = eingabe.datum, betragCent = eingabe.betragCent, titel = eingabe.titel,
                            pdfPfad = name, ocrText = ocrText, erstellt = System.currentTimeMillis(),
                        ),
                    )
                    seiten.aktuell.forEach { File(it).delete() }
                }
                val rest = seiten.naechsteRechnung()
                _state.value = ErfassungState(seiten = rest)
                fertig(!rest.leer)
            } catch (e: Exception) {
                Log.e(TAG, "Speichern fehlgeschlagen", e)
                _state.update { it.copy(beschaeftigt = false, fehler = R.string.error_save) }
            }
        }
    }

    private fun aendern(f: (Seitenstapel<String>) -> Seitenstapel<String>) = _state.update { it.copy(seiten = f(it.seiten)) }

    private fun arbeiten(block: suspend () -> Unit) {
        _state.update { it.copy(beschaeftigt = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                _state.update { it.copy(beschaeftigt = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Seite konnte nicht übernommen werden", e)
                _state.update { it.copy(beschaeftigt = false, fehler = R.string.error_page) }
            }
        }
    }

    companion object {
        private const val TAG = "Erfassung"
        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            viewModelFactory { initializer { ErfassungViewModel(container, context.applicationContext) } }
    }
}
