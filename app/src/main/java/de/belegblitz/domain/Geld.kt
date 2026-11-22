package de.belegblitz.domain

import java.util.Locale

/** Betragsumrechnung zwischen Cent und Texteingabe/-anzeige im deutschen Format. */
object Geld {
    /** "12,50", "12.5" oder "12" -> Cent; null bei ungültiger Eingabe. */
    fun parseCent(text: String): Long? {
        val t = text.trim().replace(',', '.')
        if (!Regex("""\d+(\.\d{1,2})?""").matches(t)) return null
        val teile = t.split('.')
        val euro = teile[0].toLongOrNull() ?: return null
        val cent = if (teile.size > 1) teile[1].padEnd(2, '0').toLong() else 0L
        return euro * 100 + cent
    }

    fun formatEuro(cent: Long): String =
        String.format(Locale.GERMANY, "%,d,%02d €", cent / 100, cent % 100)
}