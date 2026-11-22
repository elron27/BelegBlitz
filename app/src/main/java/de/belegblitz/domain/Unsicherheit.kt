package de.belegblitz.domain

/** SR-e268ffbc5098: Felder, die der Nutzer noch bestätigen soll (unsicher erkannt und noch nicht angefasst). */
object Unsicherheit {
    fun offen(vorschlag: RechnungVorschlag?, bestaetigt: Set<VorschlagFeld>): Set<VorschlagFeld> =
        (vorschlag?.unsicher ?: emptySet()) - bestaetigt
}
