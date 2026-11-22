package de.belegblitz.domain.ocr

import de.belegblitz.domain.Kasse
import java.time.LocalDate

/** Hinweiscode einer Kasse mit Erläuterung (kann auf einer anderen Seite stehen). SR-a6fc7a6cdbe1. */
data class Hinweis(val code: String, val text: String?)

/**
 * Eine Position eines Bescheids = eine Rechnung/ein Beleg (SR-93cb9591c1a1).
 * DAK-Brief: nur [bezugsdatum] und [erstattungCent]; [rechnungsbetragCent] und [fachrichtung] bleiben null.
 */
data class BescheidZeile(
    val bezugsdatum: LocalDate,
    val rechnungsbetragCent: Long? = null,
    val erstattungCent: Long? = null,
    val fachrichtung: String? = null,
    val sicher: Boolean = true,
)

/** Ergebnis der Bescheid-Auswertung (SR-dbb8a31cd03b, SR-a6fc7a6cdbe1, SR-93cb9591c1a1). */
data class BescheidErgebnis(
    val kasse: Wert<Kasse>?,
    val schreibdatum: Wert<LocalDate>?,
    val positionen: List<BescheidZeile>,
    /** Erstattung/Auszahlung des ganzen Bescheids. */
    val erstattungGesamtCent: Wert<Long>?,
    val hinweise: List<Hinweis>,
    /** 0-basierter Index der Seite mit den Abrechnungsdaten (bei mehrseitigen Bescheiden). */
    val datenSeite: Int?,
) {
    /** Text für das Hinweisfeld der Einreichung: "0240: Erläuterung | 0899: Erläuterung". */
    fun hinweisText(): String? =
        hinweise.takeIf { it.isNotEmpty() }?.joinToString("\n") { h -> if (h.text.isNullOrBlank()) h.code else "${h.code}: ${h.text}" }
}

/** Liest Kasse, Datum, Bezugsrechnung, Beträge und Hinweise aus dem OCR-Text eines Bescheids. */
object BescheidParser {
    private val DAK = Regex("""\bDAK\b""")
    private val VKB = Regex("""\bVKB\b|beamtenkrankenkasse|versicherungskammer""", RegexOption.IGNORE_CASE)
    private val BEZUG = Regex(
        """(?:Beleg|Rechnung(?:sdatum)?)\s*(?:vom|vom:|:)?\s*(\d{1,2}\.\d{1,2}\.(?:\d{4}|\d{2}))""", RegexOption.IGNORE_CASE,
    )
    private val DAK_SATZ = Regex(
        """Rechnung\s+vom\s+(\d{1,2}\.\d{1,2}\.\d{4})\s+in\s+H(?:ö|oe|o)he\s+von\s+(-?\d{1,3}(?:\.\d{3})*,\d{2})\s*(?:Euro|EUR|€)""",
        RegexOption.IGNORE_CASE,
    )
    private val GESAMTSUMME = Regex("""Gesamtsumme|Gesamtauszahlung|Auszahlungsbetrag|Erstattungsbetrag""", RegexOption.IGNORE_CASE)
    private val CODE_ZEILE = Regex("""^(\d{4})(?:\s+(.*))?$""")
    private val ENDE_ERLAEUTERUNG = Regex("""^(Wussten Sie|https?://|Freundliche Gr|Mit freundlichen)""", RegexOption.IGNORE_CASE)

    /** [seiten] = Text je Seite in Reihenfolge (auch einseitige Briefe). */
    fun auswerten(seiten: List<String>): BescheidErgebnis {
        val alle = OcrWerkzeug.zeilen(seiten.joinToString("\n"))
        val gesamt = alle.joinToString("\n")
        val kasse = kasse(gesamt)
        val datenSeite = datenSeite(seiten)
        val daten = datenSeite?.let { OcrWerkzeug.zeilen(seiten[it]) } ?: alle

        // DAK-Brief: "Rechnung vom 28.07.2026 in Höhe von 60,00 Euro" (Satz kann über Zeilen umbrochen sein)
        val dakSatz = DAK_SATZ.find(OcrWerkzeug.zusammenfuegen(alle))
        val fachrichtung = Fachrichtungen.erkennen(gesamt, mitLeistungen = false)?.wert
        val positionen: List<BescheidZeile>
        val summe: Wert<Long>?
        if (dakSatz != null && !gesamt.contains("Beleg vom")) {
            val d = OcrWerkzeug.daten(dakSatz.groupValues[1]).first()
            val e = OcrWerkzeug.cent(dakSatz.groupValues[2])
            positionen = listOf(BescheidZeile(d, erstattungCent = e))
            summe = e?.let { Wert(it) }
        } else {
            val (p, s) = tabelle(daten, fachrichtung)
            positionen = p
            summe = s
        }
        return BescheidErgebnis(
            kasse = kasse,
            schreibdatum = schreibdatum(seiten, datenSeite),
            positionen = positionen,
            erstattungGesamtCent = summe,
            hinweise = hinweise(seiten, datenSeite),
            datenSeite = datenSeite,
        )
    }

    private fun kasse(text: String): Wert<Kasse>? {
        val d = DAK.findAll(text).count()
        val v = VKB.findAll(text).count()
        return when {
            d == 0 && v == 0 -> null
            d > v -> Wert(Kasse.DAK, sicher = v == 0 || d >= 2 * v)
            v > d -> Wert(Kasse.VKB, sicher = d == 0 || v >= 2 * d)
            else -> Wert(Kasse.DAK, sicher = false)
        }
    }

    /** Relevante Seite = die mit den meisten Abrechnungsmerkmalen; null, wenn keine Seite welche hat. */
    internal fun datenSeite(seiten: List<String>): Int? {
        val merkmale = listOf(
            Regex("""Beleg vom""", RegexOption.IGNORE_CASE), GESAMTSUMME, Regex("""Rechnungs-?\s*betrag""", RegexOption.IGNORE_CASE),
            Regex("""Auszahlung""", RegexOption.IGNORE_CASE), Regex("""Rechnung vom""", RegexOption.IGNORE_CASE),
            OcrWerkzeug.BETRAG,
        )
        val punkte = seiten.map { s -> merkmale.sumOf { it.findAll(s).count().coerceAtMost(3) } }
        val best = punkte.withIndex().maxByOrNull { it.value } ?: return null
        return if (best.value >= 3) best.index else null
    }

    /** Schreibdatum: "Datum <d>" (auch "24 08 2026"), "Abrechnung vom <d>", sonst alleinstehende Datumszeile der Datenseite. */
    private fun schreibdatum(seiten: List<String>, datenSeite: Int?): Wert<LocalDate>? {
        val s = seiten.getOrNull(datenSeite ?: 0) ?: return null
        val zeilen = OcrWerkzeug.zeilen(s)
        for (z in zeilen) {
            val m = Regex("""^Datum\b[:\s]*(.*)$""", RegexOption.IGNORE_CASE).find(z) ?: continue
            OcrWerkzeug.daten(m.groupValues[1], mitLeerzeichen = true).firstOrNull()?.let { return Wert(it) }
        }
        for (seite in seiten) {
            Regex("""Abrechnung vom\s+(\d{1,2}\.\d{1,2}\.\d{4})""").find(seite)
                ?.let { return Wert(OcrWerkzeug.daten(it.groupValues[1]).first()) }
        }
        zeilen.firstOrNull { l -> OcrWerkzeug.daten(l).size == 1 && l.replace(Regex("""[\d.]"""), "").isBlank() }
            ?.let { return Wert(OcrWerkzeug.daten(it).first(), sicher = true) }
        // Rückfall: erstes Datum, das kein Bezugsdatum ist
        val bezug = BEZUG.findAll(zeilen.joinToString("\n")).flatMap { OcrWerkzeug.daten(it.groupValues[1]) }.toSet()
        return zeilen.flatMap { OcrWerkzeug.daten(it) }.firstOrNull { it !in bezug }?.let { Wert(it, sicher = false) }
    }

    /**
     * Abrechnungstabelle (VKB): je "Beleg vom <d>" eine Position. Rechnungsbetrag = erster Betrag nach der Beleg-Zeile.
     * Erstattung: bei einer Position die Gesamtsumme; bei mehreren aus den Kürzungen (erstattungsfähig = Betrag - Kürzung),
     * sicher nur, wenn die Summe zur Gesamtsumme passt.
     */
    private fun tabelle(zeilen: List<String>, fachrichtung: String?): Pair<List<BescheidZeile>, Wert<Long>?> {
        val ende = zeilen.indexOfFirst { GESAMTSUMME.containsMatchIn(it) }.let { if (it < 0) zeilen.size else it }
        val summe = if (ende < zeilen.size) {
            (OcrWerkzeug.betraege(zeilen[ende]) + zeilen.drop(ende + 1).take(2).flatMap { OcrWerkzeug.betraege(it) })
                .firstOrNull { it >= 0 }?.let { Wert(it) }
        } else null

        val kopf = (0 until ende).filter { Regex("""^(Beleg|Rechnung)\s+vom\b""", RegexOption.IGNORE_CASE).containsMatchIn(zeilen[it]) }
        if (kopf.isEmpty()) {
            // Einzelnes Bezugsdatum irgendwo im Text (z. B. anderer Bescheid-Aufbau)
            val d = zeilen.flatMap { l -> BEZUG.findAll(l).flatMap { OcrWerkzeug.daten(it.groupValues[1]) } }.firstOrNull()
                ?: return emptyList<BescheidZeile>() to summe
            return listOf(BescheidZeile(d, erstattungCent = summe?.wert, fachrichtung = fachrichtung, sicher = false)) to summe
        }
        val teile = kopf.mapIndexed { n, start ->
            val bis = kopf.getOrNull(n + 1) ?: ende
            val datum = OcrWerkzeug.daten(zeilen[start]).firstOrNull()
            // Fachrichtung steht im Kopf direkt davor ("Zahnarztrechnung")
            val vorher = zeilen.getOrNull(start - 1).orEmpty()
            Triple(datum, zeilen.subList(start + 1, bis), Fachrichtungen.erkennen(vorher, mitLeistungen = false)?.wert ?: fachrichtung)
        }.filter { it.first != null }

        val einzeln = teile.size == 1
        val zeilenListe = teile.map { (datum, inhalt, fr) ->
            val betraege = inhalt.flatMap { OcrWerkzeug.betraege(it) }
            val rechnung = betraege.firstOrNull { it > 0 }
            val kuerzungen = betraege.filter { it < 0 }
            val aus = if (einzeln) summe?.wert else if (rechnung == null || kuerzungen.isEmpty()) null
            else kuerzungen.sumOf { k -> (rechnung + k).coerceAtLeast(0) }
            BescheidZeile(datum!!, rechnung, aus, fr, sicher = einzeln && aus != null)
        }
        val passt = summe != null && zeilenListe.all { it.erstattungCent != null } &&
            zeilenListe.sumOf { it.erstattungCent!! } == summe.wert
        val sicher = if (einzeln) zeilenListe.all { it.erstattungCent != null } else passt
        return zeilenListe.map { it.copy(sicher = sicher) } to summe
    }

    /** Codes der Hinweis-Spalte (Datenseite) mit Erläuterung von beliebiger Seite; unerklärte Codes bleiben ohne Text. */
    private fun hinweise(seiten: List<String>, datenSeite: Int?): List<Hinweis> {
        val erklaerungen = LinkedHashMap<String, String>()
        for ((i, s) in seiten.withIndex()) {
            val nurMitText = i == datenSeite // auf der Datenseite stehen Codes auch allein in der Tabelle
            val z = OcrWerkzeug.zeilen(s)
            var aktuell: String? = null
            val puffer = mutableListOf<String>()
            fun abschliessen() {
                aktuell?.let { c -> if (puffer.isNotEmpty()) erklaerungen.putIfAbsent(c, OcrWerkzeug.zusammenfuegen(puffer)) }
                aktuell = null; puffer.clear()
            }
            for (l in z) {
                val m = CODE_ZEILE.find(l)
                when {
                    m != null && (m.groupValues[2].length >= 8 || (!nurMitText && m.groupValues[2].isEmpty())) -> {
                        abschliessen()
                        aktuell = m.groupValues[1]
                        if (m.groupValues[2].isNotEmpty()) puffer += m.groupValues[2]
                    }
                    aktuell != null && ENDE_ERLAEUTERUNG.containsMatchIn(l) -> abschliessen()
                    aktuell != null -> puffer += l
                }
            }
            abschliessen()
        }
        // Codes aus der Hinweis-Spalte der Datenseite
        val daten = OcrWerkzeug.zeilen(seiten.getOrNull(datenSeite ?: 0).orEmpty())
        val von = daten.indexOfFirst { it.equals("Hinweis", ignoreCase = true) }
        val bis = daten.indexOfFirst { GESAMTSUMME.containsMatchIn(it) }.let { if (it < 0) daten.size else it }
        val codes = if (von >= 0 && von < bis) {
            daten.subList(von + 1, bis).mapNotNull { Regex("""(?:^|\s)(\d{4})$""").find(it)?.groupValues?.get(1) }
        } else emptyList()
        return codes.distinct().map { Hinweis(it, erklaerungen[it]) }
    }
}
