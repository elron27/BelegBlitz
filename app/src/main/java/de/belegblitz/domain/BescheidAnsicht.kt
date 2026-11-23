package de.belegblitz.domain

import java.time.LocalDate

/** SR-1d598be18236: Reiter der unteren Leiste. */
enum class Reiter { RECHNUNGEN, BESCHEIDE }

data class BescheidKopf(val id: Long, val kasse: Kasse, val datum: LocalDate, val dokumentPfad: String?)

data class BescheidKarte(val bescheid: BescheidKopf, val rechnungIds: List<Long>)

/** SR-1d598be18236: Aufbereitung der Bescheid-Liste (reine Logik). */
object BescheidAnsicht {

    /** Neueste zuerst; je Bescheid die zugeordneten Rechnungen ohne Doppelte. Leere Liste = leerer Zustand. */
    fun karten(bescheide: List<BescheidKopf>, positionen: List<Pair<Long, Long>>): List<BescheidKarte> =
        bescheide.sortedWith(compareByDescending<BescheidKopf> { it.datum }.thenByDescending { it.id }).map { b ->
            BescheidKarte(b, positionen.filter { it.first == b.id }.map { it.second }.distinct())
        }

    /** Nur ein Bescheid mit Dokument lässt sich öffnen. */
    fun kannOeffnen(b: BescheidKopf) = !b.dokumentPfad.isNullOrBlank()
}
