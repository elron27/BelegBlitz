package de.belegblitz.domain

import java.time.LocalDate

/** Schlanke Sicht auf eine Einreichung für die Erinnerung (SR-aada699191a1). */
data class EinreichungSicht(
    val id: Long,
    val einreichungsdatum: LocalDate?,
    val status: EinreichungStatus,
)

/**
 * SR-aada699191a1: Erinnerung an Einreichungen ohne Bescheid.
 * Es gibt bewusst keine Fristen-Erinnerung; maßgeblich ist nur die Wartezeit seit der Einreichung.
 */
object Erinnerung {
    const val VORGABE_WOCHEN = 4
    const val MIN_WOCHEN = 1
    const val MAX_WOCHEN = 52

    fun begrenzeWochen(wochen: Int): Int = wochen.coerceIn(MIN_WOCHEN, MAX_WOCHEN)

    /** Eingereicht, noch ohne Bescheid (Status unverändert EINGEREICHT) und Wartezeit abgelaufen. */
    fun faellige(einreichungen: List<EinreichungSicht>, heute: LocalDate, wartezeitWochen: Int): List<EinreichungSicht> {
        val wochen = begrenzeWochen(wartezeitWochen).toLong()
        return einreichungen.filter {
            val datum = it.einreichungsdatum
            it.status == EinreichungStatus.EINGEREICHT && datum != null && !heute.isBefore(datum.plusWeeks(wochen))
        }
    }
}
