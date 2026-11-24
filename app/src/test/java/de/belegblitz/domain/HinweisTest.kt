package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #17: Hinweis zu Rechnung und Bescheid. */
class HinweisTest {
    @Test fun `Issue 17 leerer oder blanker Hinweis wird entfernt`() {
        assertNull(Hinweis.bereinigen(""))
        assertNull(Hinweis.bereinigen("   \n "))
        assertNull(Hinweis.bereinigen(null))
    }

    @Test fun `Issue 17 Hinweis wird getrimmt gespeichert`() {
        assertEquals("Rückfrage offen", Hinweis.bereinigen("  Rückfrage offen \n"))
    }

    @Test fun `Issue 17 Volltextsuche findet Hinweis von Rechnung und Bescheid`() {
        val dokumente = listOf(
            SuchDokument(SuchDokument.Art.RECHNUNG, 1, "Zahnarzt_Schmidt_20260215", "Füllung", 1, "Beleg fehlt noch"),
            SuchDokument(SuchDokument.Art.BESCHEID, 2, "DAK 01.04.2026", null, 1, "Widerspruch prüfen"),
            SuchDokument(SuchDokument.Art.RECHNUNG, 3, "Hausarzt_Mueller_20260101", "Grippe", 3),
        )
        val r = Volltextsuche.suchen(dokumente, "beleg")
        assertEquals(listOf(1L), r.map { it.dokument.id })
        assertEquals("Füllung\nBeleg fehlt noch".replace("\n", " "), r.single().auszug) // Auszug enthält auch den Hinweis
        assertEquals(listOf(2L), Volltextsuche.suchen(dokumente, "WIDERSPRUCH").map { it.dokument.id })
        // Hinweis und OCR-Text zusammen: alle Begriffe müssen irgendwo vorkommen
        assertEquals(listOf(1L), Volltextsuche.suchen(dokumente, "füllung beleg").map { it.dokument.id })
    }
}
