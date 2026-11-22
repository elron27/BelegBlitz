package de.belegblitz.domain.ocr

import de.belegblitz.domain.Kasse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Auswertung gegen anonymisierte Rohausgabe der echten ML-Kit-Erkennung (Emulator): Spalten werden zeilenweise
 * durcheinander geliefert, Dezimalkomma wird zu Punkt, Wörter sind verstümmelt (app/src/test/resources/ocr/echt-*).
 */
class OcrEchtTextTest {
    private fun text(name: String) = checkNotNull(javaClass.getResource("/ocr/$name.txt")) { name }.readText(Charsets.UTF_8)
    private val d = LocalDate.of(2026, 7, 28)
    private val vkb get() = BescheidParser.auswerten((1..3).map { text("echt-vkb-bescheid-seite$it") })

    @Test fun `SR-a7d7afd41c64 echte OCR Rechnung Praxis Datum Betrag`() {
        val r = RechnungParser.auswerten(listOf(text("echt-rechnung-zahnarzt")))
        assertEquals("Zahnarzt", r.fachrichtung?.wert)
        assertEquals(d, r.datum?.wert)
        assertEquals(14000L, r.betragCent?.wert)
        assertNotNull(r.praxis)
        assertTrue(r.praxis!!.wert.contains("Chiemgau"))
    }

    @Test fun `SR-dbb8a31cd03b echte OCR DAK Brief Datum und Erstattung`() {
        val b = BescheidParser.auswerten(listOf(text("echt-dak-bescheid")))
        assertEquals(Kasse.DAK, b.kasse?.wert)
        assertEquals(LocalDate.of(2026, 8, 24), b.schreibdatum?.wert)
        assertEquals(1, b.positionen.size)
        assertEquals(d, b.positionen[0].bezugsdatum)
        assertEquals(6000L, b.positionen[0].erstattungCent)
        assertNull(b.positionen[0].rechnungsbetragCent)
    }

    @Test fun `SR-dbb8a31cd03b echte OCR VKB Bescheid Rechnungsbetrag und Auszahlung trotz Spaltenreihenfolge`() {
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

    @Test fun `SR-a6fc7a6cdbe1 echte OCR VKB Hinweiscodes mit Erlaeuterung`() {
        val h = vkb.hinweise
        assertEquals(listOf("0240", "0899"), h.map { it.code })
        assertTrue(h[0].text!!.startsWith("Die Aufwendungen für prophylaktische Leistungen"))
        assertTrue(h[1].text!!.startsWith("Nach Ihrem Tarif"))
        assertTrue(h[1].text!!.contains("Prophylaxemaßnahmen"))
        assertFalse(h[1].text!!.contains("Wussten Sie"))
        assertFalse(h[1].text!!.contains("KAMMER"))
        assertTrue(vkb.hinweisText()!!.lines().size == 2)
    }

    @Test fun `SR-dbb8a31cd03b echte OCR Seitenreihenfolge egal`() {
        val b = BescheidParser.auswerten(listOf(3, 1, 2).map { text("echt-vkb-bescheid-seite$it") })
        assertEquals(1, b.datenSeite)
        assertEquals(14000L, b.positionen[0].rechnungsbetragCent)
        assertEquals(listOf("0240", "0899"), b.hinweise.map { it.code })
    }
}
