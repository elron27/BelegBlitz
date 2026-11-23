package de.belegblitz.domain

import java.time.LocalDate

/** Erzeugt den Dokumenttitel `Fachrichtung_Praxis_JJJJMMTT` (dateinamensicher). */
object Titel {
    fun erzeugen(fachrichtung: String, praxis: String, datum: LocalDate): String {
        val tag = "%04d%02d%02d".format(datum.year, datum.monthValue, datum.dayOfMonth)
        return "${teil(fachrichtung)}_${teil(praxis)}_$tag"
    }

    /** Leerzeichen -> Bindestrich, Umlaute umschreiben, übrige Sonderzeichen entfernen. */
    fun teil(text: String): String {
        val sb = StringBuilder()
        for (c in text.trim()) {
            when {
                c == 'ä' -> sb.append("ae")
                c == 'ö' -> sb.append("oe")
                c == 'ü' -> sb.append("ue")
                c == 'Ä' -> sb.append("Ae")
                c == 'Ö' -> sb.append("Oe")
                c == 'Ü' -> sb.append("Ue")
                c == 'ß' -> sb.append("ss")
                c.isWhitespace() || c == '-' || c == '_' -> sb.append('-')
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' -> sb.append(c)
                else -> Unit
            }
        }
        return sb.toString().replace(Regex("-+"), "-").trim('-')
    }

    /**
     * SR-010571e2f9d9 (SR-e2f9d9): Anzeige-Variante des Titels. Nach `_` und `-` steht ein Zero-Width-Space (U+200B),
     * damit die Liste nur dort umbrechen kann und nie mitten im Wort. Der gespeicherte Titel bleibt unverändert.
     */
    fun fuerAnzeige(titel: String): String =
        titel.replace("_", "_​").replace("-", "-​")
}
