package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.startCoroutine
import java.time.LocalDate

class ErfassungTest {
    // SR-166e5ad565ff, Kriterium 1: Fotos mehrerer Dokumente zusammenfügen
    @Test fun `SR-166e5ad565ff Kriterium 1 Seiten zusammenfuegen`() {
        val s = Seitenstapel<String>().hinzufuegen("a").hinzufuegen(listOf("b", "c"))
        assertEquals(listOf("a", "b", "c"), s.aktuell)
        val geloest = s.loesen(0)
        assertEquals(listOf("a"), geloest.geloest)
        assertEquals(listOf("b", "c", "a"), geloest.zurueckholen(0).aktuell)
    }

    // SR-166e5ad565ff, Kriterium 2: lösen
    @Test fun `SR-166e5ad565ff Kriterium 2 Seite loesen und naechste Rechnung`() {
        val s = Seitenstapel(listOf("a", "b", "c")).loesen(1)
        assertEquals(listOf("a", "c"), s.aktuell)
        assertEquals(listOf("b"), s.geloest)
        assertEquals(listOf("b"), s.naechsteRechnung().aktuell)
        assertTrue(s.naechsteRechnung().geloest.isEmpty())
        assertEquals(s, s.loesen(9))
    }

    // SR-166e5ad565ff, Kriterium 2: Reihenfolge ändern
    @Test fun `SR-166e5ad565ff Kriterium 2 Reihenfolge aendern`() {
        val s = Seitenstapel(listOf("a", "b", "c"))
        assertEquals(listOf("b", "c", "a"), s.verschieben(0, 2).aktuell)
        assertEquals(listOf("c", "a", "b"), s.verschieben(2, 0).aktuell)
        assertEquals(s, s.verschieben(0, 5))
    }

    // SR-8d2f40b64013, Kriterium 1: eine PDF-Seite je Foto, Format passt zum Foto
    @Test fun `SR-8d2f40b64013 Kriterium 1 Seitengroesse und Skalierung`() {
        assertEquals(1f, Bildgroesse.faktor(1200, 1600), 0f)
        assertEquals(0.5f, Bildgroesse.faktor(3600, 1800), 0.0001f)
        assertEquals(842, Bildgroesse.pdfHoehe(595, 1000, 1415))
        assertEquals(90, Bildgroesse.exifGrad(6))
        assertEquals(0, Bildgroesse.exifGrad(1))
    }

    // SR-619cbca1ae6e: Titel aus Prüfbildschirm, Fehleingaben abgelehnt
    @Test fun `SR-619cbca1ae6e Eingabe pruefen und Titel`() {
        val e = RechnungEingabe.pruefen("MVZ Chiemgau lacht", "Zahnarzt", "28.07.2026", "123,45")
        assertNotNull(e)
        assertEquals("Zahnarzt_MVZ-Chiemgau-lacht_20260728", e!!.titel)
        assertEquals(12345L, e.betragCent)
        assertEquals(LocalDate.of(2026, 7, 28), e.datum)
        assertNull(RechnungEingabe.pruefen("", "Zahnarzt", "28.07.2026", "1"))
        assertNull(RechnungEingabe.pruefen("X", "Zahnarzt", "31.02.2026", "1"))
        assertNull(RechnungEingabe.pruefen("X", "Zahnarzt", "28.07.2026", "abc"))
    }

    // SR-619cbca1ae6e: Platzhalter-Erkenner lässt Felder leer
    @Test fun `SR-619cbca1ae6e Platzhalter Erkenner liefert leeren Vorschlag`() {
        var v: RechnungVorschlag? = null
        // suspend-Aufruf ohne Coroutinen-Bibliothek im Test: Fortsetzung direkt ausführen
        val f: suspend () -> RechnungVorschlag = { KeinErkenner.erkennen(listOf("x.jpg")) }
        f.startCoroutine(object : kotlin.coroutines.Continuation<RechnungVorschlag> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<RechnungVorschlag>) { v = result.getOrThrow() }
        })
        assertEquals(RechnungVorschlag.LEER, v)
    }
}
