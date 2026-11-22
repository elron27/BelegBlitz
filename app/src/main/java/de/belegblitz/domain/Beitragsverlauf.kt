package de.belegblitz.domain

import java.time.LocalDate
import java.time.YearMonth

data class BeitragsEintrag(val gueltigAb: LocalDate, val monatsbetragCent: Long)

/** SR-984a2c74ebc1: Aus einem Verlauf von Einträgen ergibt sich der Beitrag je Monat. */
object Beitragsverlauf {
    /**
     * Ein Eintrag gilt ab dem Monat, in den sein Datum fällt. Maßgeblich ist der Eintrag mit dem
     * spätesten Datum bis Monatsende; gibt es keinen, ist der Beitrag unbekannt (null).
     */
    fun fuerMonat(eintraege: List<BeitragsEintrag>, monat: YearMonth): Long? {
        val ende = monat.atEndOfMonth()
        return eintraege.filter { !it.gueltigAb.isAfter(ende) }.maxByOrNull { it.gueltigAb }?.monatsbetragCent
    }
}