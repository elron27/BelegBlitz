package de.belegblitz.domain.matching

import de.belegblitz.domain.ocr.BescheidZeile
import de.belegblitz.domain.ocr.Fachrichtungen
import java.time.LocalDate

/** Rechnung, wie sie die Zuordnung braucht (unabhängig von Room). */
data class RechnungMerkmale(val id: Long, val datum: LocalDate, val fachrichtung: String, val betragCent: Long)

/** Merkmale aus dem Bescheid; null = im Bescheid nicht genannt, wird übersprungen (SR-dbb8a31cd03b). */
data class Suchmerkmale(val rechnungsdatum: LocalDate?, val fachrichtung: String?, val rechnungsbetragCent: Long?) {
    companion object {
        /** DAK-Brief hat nur Datum: Fachrichtung und Betrag sind null und werden nicht verglichen. */
        fun von(z: BescheidZeile) = Suchmerkmale(z.bezugsdatum, z.fachrichtung, z.rechnungsbetragCent)
    }
}

data class Kandidat(val rechnungId: Long, val datumPasst: Boolean, val fachrichtungPasst: Boolean?, val betragPasst: Boolean?)

/** Liste der möglichen Rechnungen, beste zuerst. */
data class Zuordnungsergebnis(val kandidaten: List<Kandidat>) {
    /** Rechnung, die ohne Rückfrage zugeordnet werden kann; null = Nutzer wählt (Kriterium 3) oder ordnet von Hand zu (Kriterium 4). */
    val eindeutig: Kandidat?
        get() = when {
            kandidaten.size == 1 -> kandidaten[0]
            kandidaten.size > 1 && kandidaten[0].vollstaendig() && !kandidaten[1].vollstaendig() -> kandidaten[0]
            else -> null
        }
}

private fun Kandidat.vollstaendig() = fachrichtungPasst != false && betragPasst != false

/** SR-dbb8a31cd03b: Bescheid -> Rechnung in der Reihenfolge Rechnungsdatum, Fachrichtung, Rechnungsbetrag. */
object Zuordnung {
    /**
     * Rechnungsdatum ist Pflichtmerkmal, wenn der Bescheid es nennt (ohne gleiches Datum kein Kandidat; Nutzer ordnet
     * dann von Hand zu). Danach wird nach Fachrichtung, dann nach Betrag sortiert. Nicht genannte Merkmale zählen nicht;
     * ein genanntes, aber abweichendes Merkmal senkt nur den Rang (Tippfehler der OCR sollen Kandidaten nicht verlieren).
     * Nennt der Bescheid gar kein Datum, kommen alle Rechnungen in Frage und die übrigen Merkmale entscheiden.
     */
    fun kandidaten(merkmale: Suchmerkmale, rechnungen: List<RechnungMerkmale>): Zuordnungsergebnis {
        val kandidaten = rechnungen
            .filter { merkmale.rechnungsdatum == null || it.datum == merkmale.rechnungsdatum }
            .map { r ->
                Kandidat(
                    rechnungId = r.id,
                    datumPasst = merkmale.rechnungsdatum != null, // false = Datum im Bescheid nicht genannt
                    fachrichtungPasst = merkmale.fachrichtung?.let {
                        Fachrichtungen.normalisieren(it) == Fachrichtungen.normalisieren(r.fachrichtung)
                    },
                    betragPasst = merkmale.rechnungsbetragCent?.let { it == r.betragCent },
                )
            }
        val sortiert = kandidaten.sortedWith(
            compareByDescending<Kandidat> { it.fachrichtungPasst == true }.thenByDescending { it.betragPasst == true },
        )
        return Zuordnungsergebnis(
            if (merkmale.rechnungsdatum == null) sortiert.filter { it.fachrichtungPasst == true || it.betragPasst == true } else sortiert,
        )
    }

    fun kandidaten(zeile: BescheidZeile, rechnungen: List<RechnungMerkmale>) = kandidaten(Suchmerkmale.von(zeile), rechnungen)
}
