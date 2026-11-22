package de.belegblitz.domain

import java.time.LocalDate

/** Vom Erkenner pro Seite gefundene Merkmale; null = auf der Seite nicht erkannt (z. B. Folgeseite). */
data class SeitenMerkmal(val praxis: String?, val datum: LocalDate?)

/**
 * SR-9d64ee2af904: ordnet nacheinander aufgenommene Seiten automatisch Rechnungen zu.
 * Gleiche Praxis und gleiches Datum = dieselbe Rechnung; weicht ein erkanntes Merkmal ab, beginnt eine neue Rechnung.
 * Seiten ohne erkannte Merkmale (Folgeseiten) bleiben bei der vorigen Rechnung.
 */
object Seitengruppierung {

    /** Liefert je Rechnung die Seitenindizes in Aufnahmereihenfolge. */
    fun gruppieren(seiten: List<SeitenMerkmal>): List<List<Int>> {
        val gruppen = mutableListOf<MutableList<Int>>()
        var praxis: String? = null
        var datum: LocalDate? = null
        seiten.forEachIndexed { i, m ->
            val p = normalisieren(m.praxis)
            val abweichend = gruppen.isNotEmpty() &&
                ((p != null && praxis != null && p != praxis) || (m.datum != null && datum != null && m.datum != datum))
            if (gruppen.isEmpty() || abweichend) {
                gruppen += mutableListOf(i)
                praxis = p
                datum = m.datum
            } else {
                gruppen.last() += i
                praxis = praxis ?: p
                datum = datum ?: m.datum
            }
        }
        return gruppen
    }

    private fun normalisieren(praxis: String?): String? =
        praxis?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
}

/** Die erste Gruppe bleibt die aktuelle Rechnung, alle weiteren Seiten warten als gelöste Seiten auf die nächste. */
fun <T> Seitenstapel<T>.nachGruppen(gruppen: List<List<Int>>): Seitenstapel<T> {
    if (gruppen.size < 2) return this
    val erste = gruppen.first().map { aktuell[it] }
    val rest = gruppen.drop(1).flatten().map { aktuell[it] }
    return Seitenstapel(aktuell = erste, geloest = geloest + rest)
}
