package de.belegblitz.domain

/**
 * SR-166e5ad565ff: Seiten einer Rechnung (`aktuell`) und vorübergehend gelöste Seiten (`geloest`).
 * Unveränderlich; jede Operation liefert einen neuen Stapel. Generisch, damit die Logik ohne Android testbar ist.
 */
data class Seitenstapel<T>(val aktuell: List<T> = emptyList(), val geloest: List<T> = emptyList()) {

    /** Fügt eine Seite (Foto oder importierte PDF-Seite) hinten an. */
    fun hinzufuegen(seite: T) = copy(aktuell = aktuell + seite)

    fun hinzufuegen(seiten: List<T>) = copy(aktuell = aktuell + seiten)

    /** Ändert die Seitenreihenfolge; ungültige Indizes lassen den Stapel unverändert. */
    fun verschieben(von: Int, nach: Int): Seitenstapel<T> {
        if (von !in aktuell.indices || nach !in aktuell.indices || von == nach) return this
        val neu = aktuell.toMutableList()
        neu.add(nach, neu.removeAt(von))
        return copy(aktuell = neu)
    }

    /** Löst eine Seite aus der Rechnung; sie wartet dann auf eine andere Rechnung. */
    fun loesen(index: Int): Seitenstapel<T> {
        if (index !in aktuell.indices) return this
        return copy(aktuell = aktuell.filterIndexed { i, _ -> i != index }, geloest = geloest + aktuell[index])
    }

    /** Fügt eine gelöste Seite wieder an die aktuelle Rechnung an (Zusammenfügen). */
    fun zurueckholen(index: Int): Seitenstapel<T> {
        if (index !in geloest.indices) return this
        return copy(aktuell = aktuell + geloest[index], geloest = geloest.filterIndexed { i, _ -> i != index })
    }

    /** Verwirft eine Seite der aktuellen Rechnung endgültig. */
    fun entfernen(index: Int): Seitenstapel<T> =
        if (index !in aktuell.indices) this else copy(aktuell = aktuell.filterIndexed { i, _ -> i != index })

    /** Nach dem Speichern: die gelösten Seiten bilden die nächste Rechnung. */
    fun naechsteRechnung() = Seitenstapel<T>(aktuell = geloest)

    val leer get() = aktuell.isEmpty() && geloest.isEmpty()
}
