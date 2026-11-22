package de.belegblitz.ui.erfassung

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.belegblitz.AppContainer
import de.belegblitz.R
import de.belegblitz.data.Bescheid
import de.belegblitz.data.PdfWerkzeug
import de.belegblitz.data.Rechnung
import de.belegblitz.data.ocr.seitenLesen
import de.belegblitz.domain.Geld
import de.belegblitz.domain.Kasse
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.matching.BescheidPlanung
import de.belegblitz.domain.matching.Kandidat
import de.belegblitz.domain.matching.RechnungMerkmale
import de.belegblitz.domain.ocr.BescheidParser
import de.belegblitz.domain.ocr.BescheidZeile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/** Eine editierbare Position des Bescheids (SR-93cb9591c1a1); Texte, damit der Nutzer alles ändern kann. */
data class PositionEingabe(
    val datum: String,
    val rechnungsbetrag: String,
    val erstattung: String,
    val fachrichtung: String,
    /** SR-e268ffbc5098: unsicher erkannt, bis der Nutzer die Position anfasst. */
    val unsicher: Boolean,
    val kandidaten: List<Kandidat>,
    val rechnungId: Long?,
)

data class BescheidState(
    val geladen: Boolean = false,
    val beschaeftigt: Boolean = false,
    val kasse: Kasse = Kasse.DAK,
    val kasseUnsicher: Boolean = false,
    val datum: String = "",
    val datumUnsicher: Boolean = false,
    val positionen: List<PositionEingabe> = emptyList(),
    val hinweis: String = "",
    val ocrText: String? = null,
    val rechnungen: List<Rechnung> = emptyList(),
    val fehler: Int? = null,
)

/** SR-3273b2e9382d, SR-dbb8a31cd03b, SR-93cb9591c1a1, SR-a6fc7a6cdbe1: Bescheid auswerten, prüfen, zuordnen, speichern. */
class BescheidViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(BescheidState())
    val state: StateFlow<BescheidState> = _state

    fun zuruecksetzen() { _state.value = BescheidState() }

    /** OCR und Parser über alle Seiten, danach automatische Zuordnung je Position. */
    fun auswerten(seitenPfade: List<String>) {
        if (_state.value.geladen || _state.value.beschaeftigt) return
        _state.update { it.copy(beschaeftigt = true) }
        viewModelScope.launch {
            try {
                val (rechnungen, seiten) = withContext(Dispatchers.IO) {
                    container.db.rechnungDao().alleEinmal() to container.textLeser.seitenLesen(seitenPfade, "Bescheid")
                }
                val e = BescheidParser.auswerten(seiten)
                val merkmale = rechnungen.map { it.merkmale() }
                val positionen = BescheidPlanung.planen(e, merkmale).map { p ->
                    PositionEingabe(
                        datum = p.zeile.bezugsdatum.format(RechnungEingabe.DatumFormat),
                        rechnungsbetrag = p.zeile.rechnungsbetragCent?.let(::cent).orEmpty(),
                        erstattung = p.zeile.erstattungCent?.let(::cent).orEmpty(),
                        fachrichtung = p.zeile.fachrichtung.orEmpty(),
                        unsicher = !p.zeile.sicher || p.zeile.erstattungCent == null,
                        kandidaten = p.kandidaten, rechnungId = p.rechnungId,
                    )
                }
                _state.value = BescheidState(
                    geladen = true,
                    kasse = e.kasse?.wert ?: Kasse.DAK, kasseUnsicher = e.kasse?.sicher != true,
                    datum = (e.schreibdatum?.wert ?: LocalDate.now()).format(RechnungEingabe.DatumFormat),
                    datumUnsicher = e.schreibdatum?.sicher != true,
                    positionen = positionen, hinweis = e.hinweisText().orEmpty(),
                    ocrText = seiten.joinToString("\n\n").trim().ifEmpty { null }, rechnungen = rechnungen,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Bescheid konnte nicht ausgewertet werden", e)
                val rechnungen = withContext(Dispatchers.IO) { container.db.rechnungDao().alleEinmal() }
                _state.value = BescheidState(
                    geladen = true, rechnungen = rechnungen, kasseUnsicher = true, datumUnsicher = true,
                    datum = LocalDate.now().format(RechnungEingabe.DatumFormat), fehler = R.string.error_ocr,
                )
            }
        }
    }

    fun setKasse(k: Kasse) = _state.update { it.copy(kasse = k, kasseUnsicher = false) }
    fun setDatum(t: String) = _state.update { it.copy(datum = t, datumUnsicher = false) }
    fun setHinweis(t: String) = _state.update { it.copy(hinweis = t) }
    fun fehlerQuittiert() = _state.update { it.copy(fehler = null) }

    fun positionHinzufuegen() = _state.update {
        it.copy(positionen = it.positionen + PositionEingabe("", "", "", "", false, emptyList(), null))
    }

    fun positionEntfernen(i: Int) = _state.update { it.copy(positionen = it.positionen.filterIndexed { n, _ -> n != i }) }

    /** Der Nutzer ändert ein Merkmal; Kandidaten werden neu bestimmt (Nutzerwahl bleibt erhalten). */
    fun positionAendern(i: Int, datum: String? = null, rechnungsbetrag: String? = null, erstattung: String? = null, fachrichtung: String? = null) =
        _state.update { s ->
            val p = s.positionen.getOrNull(i) ?: return@update s
            val neu = p.copy(
                datum = datum ?: p.datum, rechnungsbetrag = rechnungsbetrag ?: p.rechnungsbetrag,
                erstattung = erstattung ?: p.erstattung, fachrichtung = fachrichtung ?: p.fachrichtung, unsicher = false,
            )
            val bezug = datumOderNull(neu.datum)
            val ersetzt = if (bezug == null) neu.copy(kandidaten = emptyList()) else {
                val plan = BescheidPlanung.planen(
                    BescheidZeile(bezug, Geld.parseCent(neu.rechnungsbetrag), null, neu.fachrichtung.trim().ifEmpty { null }),
                    s.rechnungen.map { it.merkmale() },
                )
                neu.copy(kandidaten = plan.kandidaten, rechnungId = plan.rechnungId ?: neu.rechnungId)
            }
            s.copy(positionen = s.positionen.toMutableList().also { it[i] = ersetzt })
        }

    /** Kandidat gewählt oder (SR-dbb8a31cd03b Kriterium 4) von Hand zugeordnet; null = keine Rechnung. */
    fun rechnungWaehlen(i: Int, rechnungId: Long?) = _state.update { s ->
        s.copy(positionen = s.positionen.mapIndexed { n, p -> if (n == i) p.copy(rechnungId = rechnungId, unsicher = false) else p })
    }

    /**
     * Speichert PDF, Bescheid und Positionen. Erstattung ist für jede zugeordnete Position Pflicht.
     * Bei ungültiger Eingabe wird nur ein Fehler gesetzt.
     */
    fun speichern(seitenPfade: List<String>, fertig: () -> Unit) {
        val s = _state.value
        if (s.beschaeftigt || seitenPfade.isEmpty()) return
        val datum = datumOderNull(s.datum)
        val zugeordnet = s.positionen.filter { it.rechnungId != null }
        val betraege = zugeordnet.map { Geld.parseCent(it.erstattung) }
        if (datum == null || zugeordnet.isEmpty() || betraege.any { it == null }) {
            _state.update { it.copy(fehler = R.string.bescheid_invalid) }
            return
        }
        _state.update { it.copy(beschaeftigt = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val name = "Bescheid_${s.kasse.name}_${datum}_${UUID.randomUUID().toString().take(8)}.pdf"
                    PdfWerkzeug.bilderZuPdf(seitenPfade, container.pdfDatei(name))
                    container.speicher.bescheidSpeichern(
                        Bescheid(kasse = s.kasse, datum = datum, dokumentPfad = name, ocrText = s.ocrText),
                        zugeordnet.mapIndexed { n, p -> p.rechnungId!! to betraege[n]!! },
                        s.hinweis,
                    )
                    seitenPfade.forEach { java.io.File(it).delete() }
                }
                _state.value = BescheidState()
                fertig()
            } catch (e: Exception) {
                Log.e(TAG, "Bescheid speichern fehlgeschlagen", e)
                _state.update { it.copy(beschaeftigt = false, fehler = R.string.error_save_notice) }
            }
        }
    }

    private fun Rechnung.merkmale() = RechnungMerkmale(id, rechnungsdatum, fachrichtung, betragCent)
    private fun datumOderNull(t: String): LocalDate? = try {
        LocalDate.parse(t.trim(), RechnungEingabe.DatumFormat)
    } catch (e: java.time.DateTimeException) {
        null // ungültige Eingabe: der Nutzer sieht das Feld als leer/ungültig
    }

    private fun cent(c: Long) = "%d,%02d".format(c / 100, c % 100)

    companion object {
        private const val TAG = "BescheidVM"
        fun factory(container: AppContainer, @Suppress("UNUSED_PARAMETER") context: Context): ViewModelProvider.Factory =
            viewModelFactory { initializer { BescheidViewModel(container) } }
    }
}
