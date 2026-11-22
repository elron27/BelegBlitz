package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SeitengruppierungTest {
    private val d1 = LocalDate.of(2026, 3, 4)
    private val d2 = LocalDate.of(2026, 5, 9)

    // SR-9d64ee2af904, Kriterium 1: gleicher Arzt, gleiches Datum -> eine Rechnung
    @Test fun `SR-9d64ee2af904 Kriterium 1 gleiche Praxis und Datum ergeben eine Rechnung`() {
        val g = Seitengruppierung.gruppieren(
            listOf(SeitenMerkmal("Dr. Müller", d1), SeitenMerkmal("dr.  müller", d1), SeitenMerkmal(null, null)),
        )
        assertEquals(listOf(listOf(0, 1, 2)), g)
    }

    // SR-9d64ee2af904, Kriterium 2: verschiedene Rechnungen getrennt
    @Test fun `SR-9d64ee2af904 Kriterium 2 verschiedene Praxis oder Datum werden getrennt`() {
        val g = Seitengruppierung.gruppieren(
            listOf(SeitenMerkmal("Dr. Müller", d1), SeitenMerkmal("Dr. Meier", d1), SeitenMerkmal("Dr. Meier", d2), SeitenMerkmal(null, null)),
        )
        assertEquals(listOf(listOf(0), listOf(1), listOf(2, 3)), g)
    }

    // SR-9d64ee2af904, Kriterium 2: Anwendung auf den Seitenstapel
    @Test fun `SR-9d64ee2af904 Kriterium 2 Stapel teilt erste Rechnung ab`() {
        val s = Seitenstapel(listOf("a", "b", "c")).nachGruppen(listOf(listOf(0), listOf(1, 2)))
        assertEquals(listOf("a"), s.aktuell)
        assertEquals(listOf("b", "c"), s.geloest)
        assertEquals(listOf("b", "c"), s.naechsteRechnung().aktuell)
        val gleich = Seitenstapel(listOf("a", "b")).nachGruppen(listOf(listOf(0, 1)))
        assertEquals(listOf("a", "b"), gleich.aktuell)
    }

    @Test fun `SR-9d64ee2af904 Kriterium 1 leere Eingabe und Folgeseite zuerst`() {
        assertEquals(emptyList<List<Int>>(), Seitengruppierung.gruppieren(emptyList()))
        assertEquals(listOf(listOf(0, 1)), Seitengruppierung.gruppieren(listOf(SeitenMerkmal(null, null), SeitenMerkmal("X", d1))))
    }
}
