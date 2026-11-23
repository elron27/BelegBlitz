package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BescheidAnsichtTest {
    private fun b(id: Long, tag: Int, pfad: String? = "b$id.pdf") =
        BescheidKopf(id, Kasse.DAK, LocalDate.of(2026, 3, tag), pfad)

    @Test fun `SR-1d598be18236 AC1 Reiter Rechnungen und Bescheide`() =
        // seit SR-1571303342c4 stehen Übersicht und Suche dahinter
        assertEquals(listOf(Reiter.RECHNUNGEN, Reiter.BESCHEIDE), Reiter.entries.take(2))

    @Test fun `SR-1d598be18236 AC2 neueste zuerst mit zugeordneten Rechnungen`() {
        val karten = BescheidAnsicht.karten(
            listOf(b(1, 5), b(2, 20), b(3, 20)),
            listOf(1L to 10L, 1L to 11L, 1L to 10L, 3L to 12L),
        )
        assertEquals(listOf(3L, 2L, 1L), karten.map { it.bescheid.id })
        assertEquals(listOf(10L, 11L), karten.last().rechnungIds)
        assertEquals(listOf(12L), karten.first().rechnungIds)
        assertTrue(karten[1].rechnungIds.isEmpty())
    }

    @Test fun `SR-1d598be18236 AC3 Dokument oeffnen nur mit Pfad und leerer Zustand`() {
        assertTrue(BescheidAnsicht.kannOeffnen(b(1, 1)))
        assertFalse(BescheidAnsicht.kannOeffnen(b(1, 1, null)))
        assertTrue(BescheidAnsicht.karten(emptyList(), emptyList()).isEmpty())
    }
}
