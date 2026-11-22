package de.belegblitz.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** SR-619cbca1ae6e: geprüfte Eingaben des Prüfbildschirms. */
data class RechnungEingabe(
    val praxis: String,
    val fachrichtung: String,
    val datum: LocalDate,
    val betragCent: Long,
) {
    val titel: String get() = Titel.erzeugen(fachrichtung, praxis, datum)

    companion object {
        val DatumFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

        /** Gibt null zurück, wenn ein Feld fehlt oder ungültig ist. */
        fun pruefen(praxis: String, fachrichtung: String, datumText: String, betragText: String): RechnungEingabe? {
            if (Titel.teil(praxis).isEmpty() || Titel.teil(fachrichtung).isEmpty()) return null
            val datum = try {
                LocalDate.parse(datumText.trim(), DatumFormat)
            } catch (e: DateTimeException) {
                return null
            }
            val cent = Geld.parseCent(betragText) ?: return null
            return RechnungEingabe(praxis.trim(), fachrichtung.trim(), datum, cent)
        }
    }
}
