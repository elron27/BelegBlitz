package de.belegblitz.domain

import java.text.Normalizer

/** Ein durchsuchbares Dokument (Rechnung oder Bescheid) mit seinem OCR-Text. */
data class SuchDokument(
    val art: Art,
    val id: Long,
    val titel: String,
    val text: String?,
    /** Rechnung, die beim Antippen geöffnet wird (bei Bescheiden die zugeordnete). */
    val rechnungId: Long?,
    /** Issue #17: eigener Hinweis, wird mit durchsucht. */
    val hinweis: String? = null,
) {
    enum class Art { RECHNUNG, BESCHEID }
}

data class SuchTreffer(val dokument: SuchDokument, val auszug: String, val bereiche: List<IntRange>)

/** SR-84e9478a8eb5: lokale Volltextsuche über den OCR-Text; ohne Netz, Groß/Klein und Umlaute egal. */
object Volltextsuche {

    /** Kleinschreibung, ä->a, ö->o, ü->u, ß->ss, übrige Akzente entfernt. Länge je Zeichen kann wachsen (ß). */
    private fun faltenZeichen(c: Char): String = when (c) {
        'ß' -> "ss"
        else -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .lowercase()
    }

    fun normalisieren(text: String): String = buildString { text.forEach { append(faltenZeichen(it)) } }

    /** Normalisierter Text samt Zuordnung jedes normalisierten Zeichens zum Index im Original. */
    private fun mitIndex(text: String): Pair<String, IntArray> {
        val sb = StringBuilder()
        val idx = ArrayList<Int>()
        text.forEachIndexed { i, c -> faltenZeichen(c).forEach { sb.append(it); idx += i } }
        return sb.toString() to idx.toIntArray()
    }

    fun suchbegriffe(anfrage: String): List<String> =
        normalisieren(anfrage).split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()

    /** Treffer-Bereiche (Indizes im Originaltext) aller Suchbegriffe, sortiert und zusammengeführt. */
    fun bereiche(text: String, begriffe: List<String>): List<IntRange> {
        if (begriffe.isEmpty()) return emptyList()
        val (norm, idx) = mitIndex(text)
        val roh = mutableListOf<IntRange>()
        for (b in begriffe) {
            var pos = norm.indexOf(b)
            while (pos >= 0) {
                roh += idx[pos]..idx[pos + b.length - 1]
                pos = norm.indexOf(b, pos + 1)
            }
        }
        val ergebnis = mutableListOf<IntRange>()
        for (r in roh.sortedBy { it.first }) {
            val letzter = ergebnis.lastOrNull()
            if (letzter != null && r.first <= letzter.last + 1) ergebnis[ergebnis.lastIndex] = letzter.first..maxOf(letzter.last, r.last)
            else ergebnis += r
        }
        return ergebnis
    }

    /** Alle Begriffe müssen im Text oder Titel vorkommen. */
    fun trifft(dokument: SuchDokument, begriffe: List<String>): Boolean {
        if (begriffe.isEmpty()) return false
        val heu = normalisieren(listOfNotNull(dokument.titel, dokument.text, dokument.hinweis).joinToString("\n"))
        return begriffe.all { heu.contains(it) }
    }

    fun suchen(dokumente: List<SuchDokument>, anfrage: String, auszugLaenge: Int = 120): List<SuchTreffer> {
        val begriffe = suchbegriffe(anfrage)
        return dokumente.filter { trifft(it, begriffe) }.map { d ->
            val text = listOfNotNull(d.text, d.hinweis).joinToString("\n").replace(Regex("\\s+"), " ")
            val bereiche = bereiche(text, begriffe)
            val start = ((bereiche.firstOrNull()?.first ?: 0) - 30).coerceAtLeast(0)
            val ende = (start + auszugLaenge).coerceAtMost(text.length)
            // Bereiche relativ zum Auszug
            val rel = bereiche.filter { it.first >= start && it.last < ende }.map { (it.first - start)..(it.last - start) }
            SuchTreffer(d, text.substring(start, ende), rel)
        }
    }
}
