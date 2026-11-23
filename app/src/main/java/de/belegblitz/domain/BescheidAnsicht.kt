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

    /**
     * SR-9f0a44b25efd (Kriterien 1 und 2): Bescheide einer Rechnung für die Detailansicht, jeder nur einmal,
     * älteste zuerst (bei gleichem Datum nach Id).
     */
    fun fuerRechnung(bescheide: List<BescheidKopf>): List<BescheidKopf> =
        bescheide.distinctBy { it.id }.sortedWith(compareBy<BescheidKopf> { it.datum }.thenBy { it.id })

    /** Nur ein Bescheid mit Dokument lässt sich öffnen. */
    fun kannOeffnen(b: BescheidKopf) = !b.dokumentPfad.isNullOrBlank()
}
