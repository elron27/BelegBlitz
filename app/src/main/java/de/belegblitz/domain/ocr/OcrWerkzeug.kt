package de.belegblitz.domain.ocr

import java.time.DateTimeException
import java.time.LocalDate

/** SR-e268ffbc5098: ein erkannter Wert samt Sicherheit; unsichere Werte muss der Nutzer bestätigen. */
data class Wert<T>(val wert: T, val sicher: Boolean = true)

/** Kleine Helfer für OCR-Text (reine Kotlin-Funktionen, ohne Android). SR-a7d7afd41c64, SR-dbb8a31cd03b. */
internal object OcrWerkzeug {
    /** Datum mit Punkten (28.07.2026, 28.07.26) oder, wie von ML Kit bei kleinen Punkten geliefert, mit Leerzeichen. */
    private val DATUM = Regex("""(?<!\d)(\d{1,2})[.\s]\s?(\d{1,2})[.\s]\s?(\d{4}|\d{2})(?!\d)""")
    private val DATUM_PUNKT = Regex("""(?<![\d.])(\d{1,2})\.(\d{1,2})\.(\d{4}|\d{2})(?![\d])""")

    /** Betrag mit Dezimalkomma, optional Tausenderpunkt und Vorzeichen: 140,00 / 1.234,50 / -132,07. */
    val BETRAG = Regex("""(?<![\d,.])-?\d{1,3}(?:\.\d{3})*,\d{2}(?![\d])""")

    fun zeilen(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Alle Daten im Text, bei [mitLeerzeichen] auch die Schreibweise "24 08 2026". */
    fun daten(text: String, mitLeerzeichen: Boolean = false): List<LocalDate> =
        (if (mitLeerzeichen) DATUM else DATUM_PUNKT).findAll(text).mapNotNull { datum(it) }.toList()

    private fun datum(m: MatchResult): LocalDate? = try {
        val (t, mo, j) = m.destructured
        val jahr = if (j.length == 2) 2000 + j.toInt() else j.toInt()
        LocalDate.of(jahr, mo.toInt(), t.toInt())
    } catch (e: DateTimeException) {
        null
    }

    fun betraege(text: String): List<Long> = BETRAG.findAll(text).mapNotNull { cent(it.value) }.toList()

    /** "1.234,50" -> 123450; "-132,07" -> -13207. */
    fun cent(text: String): Long? {
        val negativ = text.startsWith("-")
        val t = text.removePrefix("-").replace(".", "")
        val teile = t.split(',')
        if (teile.size != 2) return null
        val v = (teile[0].toLongOrNull() ?: return null) * 100 + (teile[1].toLongOrNull() ?: return null)
        return if (negativ) -v else v
    }

    /** Trennstrich-Zeilenumbrüche auflösen ("Prophylaxe-" + "maßnahmen") und Zeilen mit Leerzeichen verbinden. */
    fun zusammenfuegen(zeilen: List<String>): String {
        val sb = StringBuilder()
        for (z in zeilen) {
            if (sb.isNotEmpty()) {
                if (sb.endsWith("-") && z.firstOrNull()?.isLowerCase() == true) sb.setLength(sb.length - 1) else sb.append(' ')
            }
            sb.append(z)
        }
        return sb.toString()
    }
}
