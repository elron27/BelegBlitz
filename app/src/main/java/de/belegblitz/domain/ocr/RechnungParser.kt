package de.belegblitz.domain.ocr

import de.belegblitz.domain.RechnungVorschlag
import de.belegblitz.domain.VorschlagFeld
import java.time.LocalDate

/** Ergebnis der Rechnungsauswertung; jeder Wert trägt seine Sicherheit (SR-e268ffbc5098). */
data class RechnungErgebnis(
    val praxis: Wert<String>? = null,
    val fachrichtung: Wert<String>? = null,
    val datum: Wert<LocalDate>? = null,
    val betragCent: Wert<Long>? = null,
    /** SR-306dc2: alle erkannten Daten, eindeutig, in Dokumentreihenfolge. */
    val datumKandidaten: List<LocalDate> = emptyList(),
) {
    /** Übersetzt in den Vorschlag für den Prüfbildschirm; [ocrText] wird mit der Rechnung gespeichert (SR-84e9478a8eb5). */
    fun alsVorschlag(ocrText: String?): RechnungVorschlag = RechnungVorschlag(
        praxis = praxis?.wert, fachrichtung = fachrichtung?.wert, datum = datum?.wert, datumKandidaten = datumKandidaten, betragCent = betragCent?.wert,
        unsicher = buildSet {
            if (praxis?.sicher == false) add(VorschlagFeld.PRAXIS)
            if (fachrichtung?.sicher == false) add(VorschlagFeld.FACHRICHTUNG)
            if (datum?.sicher == false) add(VorschlagFeld.DATUM)
            if (betragCent?.sicher == false) add(VorschlagFeld.BETRAG)
        },
        ocrText = ocrText,
    )
}

/** SR-a7d7afd41c64: Praxis, Fachrichtung, Rechnungsdatum und Gesamtbetrag aus dem OCR-Text einer Rechnung. */
object RechnungParser {
    private val PRAXIS_WORT = Regex(
        """\b(GmbH|MVZ|Praxis|Dr\.|Dres\.|Prof\.|Gemeinschaftspraxis|Zentrum|Klinik|Zahnärzte|Fachärzte|Ärzte|Partnerschaft)\b|\bmed\.""",
        RegexOption.IGNORE_CASE,
    )
    private val KEIN_PRAXISNAME = Regex("""Kontoinhaber|\bIBAN\b|\bBIC\b|Rechnung|Telefon|Tel\.|@|www\.|\bFrau\b|\bHerr\b""", RegexOption.IGNORE_CASE)

    /** [seiten] = Text je Seite in Reihenfolge. */
    fun auswerten(seiten: List<String>): RechnungErgebnis {
        val text = seiten.joinToString("\n")
        val zeilen = OcrWerkzeug.zeilen(text)
        return RechnungErgebnis(praxis(zeilen), Fachrichtungen.erkennen(text), datum(zeilen), betrag(zeilen), datumKandidaten(zeilen))
    }

    /** Praxis: erste Zeile im Briefkopf, die nach Praxis/MVZ/Arztname aussieht; sonst die erste Textzeile (unsicher). */
    internal fun praxis(zeilen: List<String>): Wert<String>? {
        val kopf = zeilen.take(12)
        kopf.firstOrNull { PRAXIS_WORT.containsMatchIn(it) && !KEIN_PRAXISNAME.containsMatchIn(it) && it.length in 4..80 }
            ?.let { return Wert(ohneRechtsform(it), sicher = true) }
        kopf.firstOrNull { l -> l.length in 4..60 && l.count { it.isLetter() } >= 4 && !KEIN_PRAXISNAME.containsMatchIn(l) }
            ?.let { return Wert(it, sicher = false) }
        return null
    }

    /** Rechtsform am Ende fällt weg ("MVZ Chiemgau lacht GmbH" -> "MVZ Chiemgau lacht"), sie gehört nicht in den Titel. */
    private fun ohneRechtsform(name: String): String =
        name.trimEnd(',', ';').replace(Regex("""[\s,]+(GmbH(\s*&\s*Co\.?\s*KG)?|mbH|GbR|PartG(\s*mbB)?|AG|e\.\s?V\.|UG(\s*\(haftungsbeschränkt\))?)$""", RegexOption.IGNORE_CASE), "")

    /** SR-306dc2: alle gültigen Daten (TT.MM.JJJJ, TT.MM.JJ) ohne Doppelte in Dokumentreihenfolge. */
    internal fun datumKandidaten(zeilen: List<String>): List<LocalDate> =
        zeilen.flatMap { OcrWerkzeug.daten(it) }.distinct()

    /** Datum: hinter "Rechnungsdatum"/"Datum:"; sonst das erste Datum, das keine Zahlungsfrist ist (unsicher). */
    internal fun datum(zeilen: List<String>): Wert<LocalDate>? {
        for (stichwort in listOf("rechnungsdatum", "belegdatum", "datum")) {
            for ((i, z) in zeilen.withIndex()) {
                val idx = z.lowercase().indexOf(stichwort)
                if (idx < 0 || z.lowercase().contains("geburt")) continue
                val d = OcrWerkzeug.daten(z.substring(idx + stichwort.length)).firstOrNull()
                    ?: zeilen.drop(i + 1).take(2).firstNotNullOfOrNull { OcrWerkzeug.daten(it).firstOrNull() }
                if (d != null) return Wert(d, sicher = stichwort == "rechnungsdatum" || stichwort == "belegdatum")
            }
        }
        val frei = zeilen.filterNot { Regex("""\bbis\b|fällig|zahlbar|Geburtsdatum""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .flatMap { OcrWerkzeug.daten(it) }
        return frei.firstOrNull()?.let { Wert(it, sicher = false) }
    }

    /**
     * Betrag: Stichwörter in Reihenfolge Rechnungsbetrag, Gesamtbetrag/Endbetrag/Summe, "zu berechnen"; Betrag
     * steht in derselben oder in einer der nächsten Zeilen. Sicher, wenn alle Stichwort-Treffer übereinstimmen.
     */
    internal fun betrag(zeilen: List<String>): Wert<Long>? {
        val stufen = listOf(
            Regex("""rechnungsbetrag|gesamtbetrag|endbetrag|rechnungssumme|gesamtsumme|zu zahlen|zahlbetrag""", RegexOption.IGNORE_CASE),
            Regex("""zu berechnen|summe|total""", RegexOption.IGNORE_CASE),
        )
        for (stichwort in stufen) {
            val treffer = mutableListOf<Long>()
            for ((i, z) in zeilen.withIndex()) {
                val m = stichwort.find(z) ?: continue
                val b = OcrWerkzeug.betraege(z.substring(m.range.last + 1)).firstOrNull()
                    ?: zeilen.drop(i + 1).take(2).firstOrNull { OcrWerkzeug.betraege(it).size == 1 && it.length < 16 }
                        ?.let { OcrWerkzeug.betraege(it).first() }
                if (b != null && b > 0) treffer += b
            }
            if (treffer.isNotEmpty()) return Wert(treffer.first(), sicher = treffer.toSet().size == 1)
        }
        // Rückfall: größter Betrag im Text, unsicher
        val alle = zeilen.flatMap { OcrWerkzeug.betraege(it) }.filter { it > 0 }
        return alle.maxOrNull()?.let { Wert(it, sicher = false) }
    }
}
