package de.belegblitz.domain

/** Ergebnis der manuellen Erstattung: Betrag und daraus abgeleiteter Status. */
data class ManuelleErstattungErgebnis(val erstattetCent: Long, val status: EinreichungStatus)

/** SR-bb429d971190: Erstattung ohne Bescheid von Hand erfassen (reine Logik). */
object ManuelleErstattung {

    /** Kriterium 1: 0 EUR ist gültig; leere oder ungültige Eingabe liefert null. */
    fun pruefen(text: String): Long? = Geld.parseCent(text)

    /** Kriterium 2: Status folgt dem Betrag (SR-40de3efa693e). */
    fun uebernehmen(eingereichtCent: Long, erstattetCent: Long) =
        ManuelleErstattungErgebnis(erstattetCent, StatusAbleitung.ausErstattung(eingereichtCent, erstattetCent))
}
