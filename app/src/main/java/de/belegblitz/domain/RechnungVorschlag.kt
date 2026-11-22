package de.belegblitz.domain

import java.time.LocalDate

/** SR-619cbca1ae6e: Vorschlag für die Felder des Prüfbildschirms; null = nicht erkannt. */
data class RechnungVorschlag(
    val praxis: String? = null,
    val fachrichtung: String? = null,
    val datum: LocalDate? = null,
    val betragCent: Long? = null,
) {
    companion object { val LEER = RechnungVorschlag() }
}

/** Platzhalter-Schnittstelle: Schritt 3 steckt hier die OCR-Erkennung ein. */
interface RechnungErkenner {
    /** @param seitenPfade Bilddateien der Seiten in Reihenfolge. */
    suspend fun erkennen(seitenPfade: List<String>): RechnungVorschlag
}

/** Standard bis zur OCR: liefert nichts, die Felder bleiben leer. */
object KeinErkenner : RechnungErkenner {
    override suspend fun erkennen(seitenPfade: List<String>) = RechnungVorschlag.LEER
}
