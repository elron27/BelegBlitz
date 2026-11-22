package de.belegblitz.domain.matching

import de.belegblitz.domain.ocr.BescheidErgebnis
import de.belegblitz.domain.ocr.BescheidZeile

/** Eine Bescheid-Position mit Kandidaten und (falls eindeutig) vorgewählter Rechnung. */
data class PositionsPlan(val zeile: BescheidZeile, val kandidaten: List<Kandidat>, val rechnungId: Long?)

/** SR-93cb9591c1a1, SR-dbb8a31cd03b: Aufteilen eines Bescheids in Positionen je Rechnung. */
object BescheidPlanung {

    /** Jede Position wird einzeln zugeordnet; eindeutige Treffer sind vorgewählt, sonst wählt der Nutzer. */
    fun planen(ergebnis: BescheidErgebnis, rechnungen: List<RechnungMerkmale>): List<PositionsPlan> =
        ergebnis.positionen.map { planen(it, rechnungen) }

    fun planen(zeile: BescheidZeile, rechnungen: List<RechnungMerkmale>): PositionsPlan {
        val erg = Zuordnung.kandidaten(zeile, rechnungen)
        return PositionsPlan(zeile, erg.kandidaten, erg.eindeutig?.rechnungId)
    }

    /** Erstattungsbeträge je Rechnung; mehrere Positionen derselben Rechnung werden addiert. */
    fun jeRechnung(zuordnungen: List<Pair<Long, Long>>): Map<Long, Long> =
        zuordnungen.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
}
