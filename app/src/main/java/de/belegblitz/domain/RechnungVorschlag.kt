package de.belegblitz.domain

import java.time.LocalDate

/** Felder des Prüfbildschirms, für die der Erkenner eine Sicherheit angibt. */
enum class VorschlagFeld { PRAXIS, FACHRICHTUNG, DATUM, BETRAG }

/**
 * SR-619cbca1ae6e: Vorschlag für die Felder des Prüfbildschirms; null = nicht erkannt.
 * [unsicher] = Felder, die der Nutzer bestätigen soll (SR-e268ffbc5098); [ocrText] wird mit der Rechnung
 * gespeichert und ist die Grundlage der Volltextsuche (SR-84e9478a8eb5).
 */
data class RechnungVorschlag(
    val praxis: String? = null,
    val fachrichtung: String? = null,
    val datum: LocalDate? = null,
    val betragCent: Long? = null,
    val unsicher: Set<VorschlagFeld> = emptySet(),
    val ocrText: String? = null,
) {
    companion object { val LEER = RechnungVorschlag() }
}

/** Schnittstelle zur Texterkennung einer Rechnung (SR-a7d7afd41c64). */
interface RechnungErkenner {
    /** @param seitenPfade Bilddateien der Seiten in Reihenfolge. */
    suspend fun erkennen(seitenPfade: List<String>): RechnungVorschlag
}

/** Ersatz ohne OCR (Tests): liefert nichts, die Felder bleiben leer. */
object KeinErkenner : RechnungErkenner {
    override suspend fun erkennen(seitenPfade: List<String>) = RechnungVorschlag.LEER
}
