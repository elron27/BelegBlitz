package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolltextsucheTest {
    private fun d(id: Long, titel: String, text: String?) =
        SuchDokument(SuchDokument.Art.RECHNUNG, id, titel, text, id)

    private val docs = listOf(
        d(1, "Orthopaedie_Mueller_20260301", "Praxis Dr. Müller Orthopädie Rechnung Gebühr 85,00 EUR"),
        d(2, "Zahnarzt_Schmidt_20260215", "Zahnarztpraxis STRASSE Schmidt Füllung"),
        d(3, "Leer", null),
    )

    @Test fun `SR-84e9478a8eb5 AC1 findet Dokumente anhand von OCR-Woertern`() {
        val t = Volltextsuche.suchen(docs, "orthopädie")
        assertEquals(listOf(1L), t.map { it.dokument.id })
    }

    @Test fun `SR-84e9478a8eb5 AC1 Gross-Klein und Umlaute sind egal`() {
        assertEquals(listOf(2L), Volltextsuche.suchen(docs, "strasse").map { it.dokument.id })
        assertEquals(listOf(2L), Volltextsuche.suchen(docs, "FÜLLUNG").map { it.dokument.id })
        assertEquals(listOf(1L), Volltextsuche.suchen(docs, "muller").map { it.dokument.id })
        assertEquals("strasse", Volltextsuche.normalisieren("Straße"))
    }

    @Test fun `SR-84e9478a8eb5 AC1 mehrere Begriffe muessen alle vorkommen`() {
        assertEquals(listOf(1L), Volltextsuche.suchen(docs, "müller gebühr").map { it.dokument.id })
        assertTrue(Volltextsuche.suchen(docs, "müller schmidt").isEmpty())
    }

    @Test fun `SR-84e9478a8eb5 AC2 Suche ist reine lokale Funktion ohne Netz und leere Anfrage findet nichts`() {
        assertTrue(Volltextsuche.suchen(docs, "  ").isEmpty())
        assertTrue(Volltextsuche.suchen(emptyList(), "x").isEmpty())
    }

    @Test fun `SR-84e9478a8eb5 Trefferhervorhebung liefert Bereiche im Originaltext`() {
        val text = "Praxis Dr. Müller Orthopädie"
        val b = Volltextsuche.bereiche(text, Volltextsuche.suchbegriffe("MULLER"))
        assertEquals(1, b.size)
        assertEquals("Müller", text.substring(b[0].first, b[0].last + 1))
        val t = Volltextsuche.suchen(docs, "müller").single()
        assertEquals("Müller", t.auszug.substring(t.bereiche[0].first, t.bereiche[0].last + 1))
    }

    @Test fun `SR-84e9478a8eb5 Titel wird ebenfalls durchsucht`() =
        assertEquals(listOf(3L), Volltextsuche.suchen(docs, "leer").map { it.dokument.id })
}
