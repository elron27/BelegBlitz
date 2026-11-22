package de.belegblitz.domain.ocr

import de.belegblitz.domain.Kasse
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.VorschlagFeld
import de.belegblitz.domain.matching.RechnungMerkmale
import de.belegblitz.domain.matching.Suchmerkmale
import de.belegblitz.domain.matching.Zuordnung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Auswertung gegen anonymisierte OCR-Texte der Testdokumente (app/src/test/resources/ocr). */
class OcrParserTest {
    private fun text(name: String) = checkNotNull(javaClass.getResource("/ocr/$name.txt")) { name }.readText(Charsets.UTF_8)

    private val rechnung get() = RechnungParser.auswerten(listOf(text("rechnung-zahnarzt-20260728")))
    private val dak get() = BescheidParser.auswerten(listOf(text("dak-bescheid-20260824")))
    private val vkb get() = BescheidParser.auswerten(listOf("seite1", "seite2", "seite3").map { text("vkb-bescheid-20260907-$it") })
    private val d = LocalDate.of(2026, 7, 28)

    // SR-a7d7afd41c64, Kriterium 1: Praxis, Fachrichtung, Datum, Betrag
    @Test fun `SR-a7d7afd41c64 Kriterium 1 Rechnung Praxis Fachrichtung Datum Betrag`() {
        val r = rechnung
        assertEquals("MVZ Chiemgau lacht", r.praxis?.wert) // Rechtsform GmbH fällt weg
        assertEquals("Zahnarzt", r.fachrichtung?.wert)
        assertEquals(d, r.datum?.wert)
        assertEquals(14000L, r.betragCent?.wert)
        assertTrue(listOfNotNull(r.praxis?.sicher, r.fachrichtung?.sicher, r.datum?.sicher, r.betragCent?.sicher).all { it })
    }

    // SR-a7d7afd41c64, Kriterium 3 (Vorschlag änderbar) + Titel Zahnarzt_MVZ-Chiemgau-lacht_20260728
    @Test fun `SR-a7d7afd41c64 Kriterium 3 Vorschlag ergibt Titel`() {
        val v = rechnung.alsVorschlag("x")
        val e = RechnungEingabe(v.praxis!!, v.fachrichtung!!, v.datum!!, v.betragCent!!)
        assertEquals("Zahnarzt_MVZ-Chiemgau-lacht_20260728", e.titel)
        assertTrue(v.unsicher.isEmpty())
    }

    // SR-e268ffbc5098: unsichere Werte werden markiert
    @Test fun `SR-e268ffbc5098 unsichere Werte werden markiert`() {
        val r = RechnungParser.auswerten(listOf("Irgendein Laden\nBeleg 03.05.2026\nSumme 12,50 EUR\nSumme 13,50 EUR"))
        assertEquals(false, r.datum?.sicher)
        assertEquals(false, r.betragCent?.sicher)
        assertTrue(r.alsVorschlag(null).unsicher.containsAll(listOf(VorschlagFeld.DATUM, VorschlagFeld.BETRAG)))
        assertNull(RechnungParser.auswerten(listOf("")).betragCent)
    }

    // SR-a7d7afd41c64: weitere Fachrichtungen per Schlüsselwort
    @Test fun `SR-a7d7afd41c64 Fachrichtungen per Schluesselwort`() {
        fun f(t: String) = Fachrichtungen.erkennen(t)?.wert
        assertEquals("Hausarzt", f("Hausarztpraxis Dr. Muster"))
        assertEquals("Augenarzt", f("Augenärzte am Markt"))
        assertEquals("Orthopäde", f("Orthopädie Zentrum"))
        assertEquals("Hautarzt", f("Dermatologie Praxis"))
        assertEquals("Frauenarzt", f("Frauenärztin Dr. Muster"))
        assertEquals("HNO", f("HNO-Praxis"))
        assertEquals("Physiotherapie", f("Physiotherapie Muster"))
        assertEquals("Radiologie", f("Radiologie Rosenheim"))
        assertEquals("Labor", f("Labor Muster MVZ"))
        assertNull(f("Autowerkstatt"))
    }

    // SR-84e9478a8eb5: Der OCR-Text geht unverändert in den Vorschlag und damit in die Rechnung
    @Test fun `SR-84e9478a8eb5 OCR-Text wird mit dem Vorschlag geliefert`() {
        val t = text("rechnung-zahnarzt-20260728")
        assertEquals(t, RechnungParser.auswerten(listOf(t)).alsVorschlag(t).ocrText)
        assertTrue(t.contains("Professionelle Zahnreinigung"))
    }

    // SR-dbb8a31cd03b, Kriterium 2: DAK-Brief nur mit Rechnungsdatum und Erstattung
    @Test fun `SR-dbb8a31cd03b Kriterium 2 DAK Brief Datum und Erstattung ohne Betrag`() {
        val b = dak
        assertEquals(Kasse.DAK, b.kasse?.wert)
        assertEquals(LocalDate.of(2026, 8, 24), b.schreibdatum?.wert)
        assertEquals(1, b.positionen.size)
        val z = b.positionen[0]
        assertEquals(d, z.bezugsdatum)
        assertEquals(6000L, z.erstattungCent)
        assertNull(z.rechnungsbetragCent)
        assertNull(z.fachrichtung)
        assertEquals(6000L, b.erstattungGesamtCent?.wert)
        assertTrue(b.hinweise.isEmpty())
    }

    // SR-dbb8a31cd03b, Kriterium 1: VKB nennt Datum, Fachrichtung und Rechnungsbetrag
    @Test fun `SR-dbb8a31cd03b Kriterium 1 VKB Bescheid Positionen`() {
        val b = vkb
        assertEquals(Kasse.VKB, b.kasse?.wert)
        assertEquals(LocalDate.of(2026, 9, 7), b.schreibdatum?.wert)
        assertEquals(0, b.datenSeite)
        assertEquals(1, b.positionen.size)
        val z = b.positionen[0]
        assertEquals(d, z.bezugsdatum)
        assertEquals(14000L, z.rechnungsbetragCent)
        assertEquals(793L, z.erstattungCent)
        assertEquals("Zahnarzt", z.fachrichtung)
        assertEquals(793L, b.erstattungGesamtCent?.wert)
        assertTrue(z.sicher)
    }

    // SR-a6fc7a6cdbe1, Kriterium 1: Codes mit Erläuterung von Seite 3
    @Test fun `SR-a6fc7a6cdbe1 Kriterium 1 Hinweiscodes mit Erlaeuterung von anderer Seite`() {
        val h = vkb.hinweise
        assertEquals(listOf("0240", "0899"), h.map { it.code })
        assertTrue(h[0].text!!.startsWith("Die Aufwendungen für prophylaktische Leistungen"))
        assertTrue(h[0].text!!.endsWith("nicht erstattungsfähig."))
        assertTrue(h[1].text!!.contains("Prophylaxemaßnahmen")) // Silbentrennung aufgelöst
        assertTrue(h[1].text!!.endsWith("nur zum Teil übernehmen."))
        assertFalse(h[1].text!!.contains("Wussten Sie"))
        assertTrue(vkb.hinweisText()!!.startsWith("0240: Die Aufwendungen"))
    }

    // SR-a6fc7a6cdbe1: Seitenreihenfolge egal, relevante Seite wird gefunden
    @Test fun `SR-a6fc7a6cdbe1 relevante Seite eines mehrseitigen Bescheids`() {
        val s = listOf("seite3", "seite1", "seite2").map { text("vkb-bescheid-20260907-$it") }
        val b = BescheidParser.auswerten(s)
        assertEquals(1, b.datenSeite)
        assertEquals(listOf("0240", "0899"), b.hinweise.map { it.code })
        assertNotNull(b.hinweise[1].text)
    }

    // SR-93cb9591c1a1, Kriterium 1: Bescheid mit mehreren Positionen wird aufgeteilt
    @Test fun `SR-93cb9591c1a1 Kriterium 1 mehrere Positionen`() {
        val s = """
            Bayerische Beamtenkrankenkasse
            07.09.2026
            Rechnung/Beleg
            Hinweis
            Zahnarztrechnung
            Beleg vom 28.07.2026
            140,00
            40,00
            Zahnarztrechnung
            Beleg vom 02.08.2026
            100,00
            30,00
            Gesamtsumme
            70,00 Euro
        """.trimIndent()
        val b = BescheidParser.auswerten(listOf(s))
        assertEquals(2, b.positionen.size)
        assertEquals(listOf(LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 2)), b.positionen.map { it.bezugsdatum })
        assertEquals(listOf(14000L, 10000L), b.positionen.map { it.rechnungsbetragCent })
        assertEquals(7000L, b.erstattungGesamtCent?.wert)
        // ohne Kürzungsspalte sind die Einzelbeträge nicht ableitbar: unsicher markiert, nicht erfunden
        assertTrue(b.positionen.none { it.sicher })
    }

    // SR-dbb8a31cd03b, Kriterium 1: Reihenfolge Rechnungsdatum, Fachrichtung, Betrag; fehlende Merkmale überspringen
    @Test fun `SR-dbb8a31cd03b Kriterium 1 Zuordnung Datum Fachrichtung Betrag`() {
        val rechnungen = listOf(
            RechnungMerkmale(1, d, "Hausarzt", 5000),
            RechnungMerkmale(2, d, "Zahnarzt", 9000),
            RechnungMerkmale(3, d, "Zahnarzt", 14000),
            RechnungMerkmale(4, LocalDate.of(2026, 7, 29), "Zahnarzt", 14000),
        )
        val z = Zuordnung.kandidaten(vkb.positionen[0], rechnungen)
        assertEquals(3L, z.eindeutig?.rechnungId)
        assertEquals(listOf(3L, 2L, 1L), z.kandidaten.map { it.rechnungId })
        // Betrag fehlt im Bescheid: wird übersprungen, Fachrichtung entscheidet nur teilweise -> Kandidaten
        val ohneBetrag = Zuordnung.kandidaten(Suchmerkmale(d, "Zahnarzt", null), rechnungen)
        assertNull(ohneBetrag.eindeutig)
        assertEquals(setOf(2L, 3L), ohneBetrag.kandidaten.take(2).map { it.rechnungId }.toSet())
        // Fachrichtung und Betrag fehlen, Datum eindeutig
        assertEquals(4L, Zuordnung.kandidaten(Suchmerkmale(LocalDate.of(2026, 7, 29), null, null), rechnungen).eindeutig?.rechnungId)
    }

    // SR-dbb8a31cd03b, Kriterium 2: DAK-Brief nur über das Datum, Betrag wird nicht verglichen
    @Test fun `SR-dbb8a31cd03b Kriterium 2 DAK Zuordnung nur ueber Datum`() {
        val rechnungen = listOf(RechnungMerkmale(7, d, "Zahnarzt", 14000), RechnungMerkmale(8, LocalDate.of(2026, 6, 1), "Zahnarzt", 6000))
        val z = Zuordnung.kandidaten(dak.positionen[0], rechnungen)
        assertEquals(7L, z.eindeutig?.rechnungId)
        assertNull(z.eindeutig?.betragPasst)
    }

    // SR-dbb8a31cd03b, Kriterien 3 und 4: mehrere Kandidaten bzw. kein Treffer
    @Test fun `SR-dbb8a31cd03b Kriterium 3 und 4 Kandidaten und kein Treffer`() {
        val zwei = listOf(RechnungMerkmale(1, d, "Zahnarzt", 100), RechnungMerkmale(2, d, "Zahnarzt", 200))
        val mehrere = Zuordnung.kandidaten(dak.positionen[0], zwei)
        assertEquals(2, mehrere.kandidaten.size)
        assertNull(mehrere.eindeutig)
        val keiner = Zuordnung.kandidaten(dak.positionen[0], listOf(RechnungMerkmale(1, LocalDate.of(2026, 1, 1), "Zahnarzt", 1)))
        assertTrue(keiner.kandidaten.isEmpty())
    }
}
