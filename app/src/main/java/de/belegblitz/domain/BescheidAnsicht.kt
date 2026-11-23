package de.belegblitz.domain

import java.time.LocalDate

/**
 * SR-1d598be18236, SR-1571303342c4: Hauptziele der unteren Leiste in fester Reihenfolge; [route] ist die Navigationsroute
 * (muss zu `Routes` passen, siehe Test).
 */
enum class Reiter(val route: String) {
    RECHNUNGEN("liste"), BESCHEIDE("bescheide"), UEBERSICHT("uebersicht"), SUCHE("suche"),
}

/** SR-70a2f4475fe0: eine Rechnung eines Bescheids mit der Summe ihrer Positionen. */
data class BescheidRechnungZeile(val rechnungId: Long, val betragCent: Long)

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

    /**
     * SR-70a2f4475fe0 (Kriterium 2): zugeordnete Rechnungen eines Bescheids; mehrere Positionen derselben Rechnung
     * werden summiert, die Reihenfolge folgt dem ersten Auftreten. Paare sind (Rechnungs-Id, Betrag in Cent).
     */
    fun rechnungsZeilen(positionen: List<Pair<Long, Long>>): List<BescheidRechnungZeile> =
        positionen.groupBy({ it.first }, { it.second }).map { (id, betraege) -> BescheidRechnungZeile(id, betraege.sum()) }

    /** SR-70a2f4475fe0: Gesamtbetrag aller Positionen. */
    fun summe(zeilen: List<BescheidRechnungZeile>): Long = zeilen.sumOf { it.betragCent }
}
