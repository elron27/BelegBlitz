package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ManuelleErstattungTest {
    @Test fun `SR-bb429d971190 AC1 null und Betraege gueltig, ungueltig abgelehnt`() {
        assertEquals(0L, ManuelleErstattung.pruefen("0"))
        assertEquals(0L, ManuelleErstattung.pruefen("0,00"))
        assertEquals(1250L, ManuelleErstattung.pruefen("12,50"))
        assertNull(ManuelleErstattung.pruefen(""))
        assertNull(ManuelleErstattung.pruefen("abc"))
        assertNull(ManuelleErstattung.pruefen("-5"))
    }

    @Test fun `SR-bb429d971190 AC2 Status folgt dem Betrag`() {
        assertEquals(EinreichungStatus.ABGELEHNT, ManuelleErstattung.uebernehmen(10_000, 0).status)
        assertEquals(EinreichungStatus.TEILWEISE_ERSTATTET, ManuelleErstattung.uebernehmen(10_000, 4_000).status)
        assertEquals(EinreichungStatus.ERSTATTET, ManuelleErstattung.uebernehmen(10_000, 10_000).status)
        assertEquals(4_000L, ManuelleErstattung.uebernehmen(10_000, 4_000).erstattetCent)
    }

    @Test fun `SR-bb429d971190 AC3 nur fuer eingereichte Einreichungen`() {
        assertFalse(Einreichungsregeln.kannErstattungEintragen(EinreichungStatus.ERFASST))
        assertTrue(Einreichungsregeln.kannErstattungEintragen(EinreichungStatus.EINGEREICHT))
        assertTrue(Einreichungsregeln.kannErstattungEintragen(EinreichungStatus.ERSTATTET))
    }

    private fun k(id: Long, d: LocalDate, pfad: String? = "x.pdf") = BescheidKopf(id, Kasse.DAK, d, pfad)

    @Test fun `SR-9f0a44b25efd AC1 jeder Bescheid nur einmal`() {
        val d = LocalDate.of(2026, 5, 1)
        assertEquals(listOf(1L), BescheidAnsicht.fuerRechnung(listOf(k(1, d), k(1, d))).map { it.id })
    }

    @Test fun `SR-9f0a44b25efd AC2 aufsteigend nach Datum dann Id`() {
        val a = LocalDate.of(2026, 5, 1)
        val b = LocalDate.of(2026, 6, 1)
        assertEquals(listOf(3L, 1L, 2L), BescheidAnsicht.fuerRechnung(listOf(k(2, b), k(3, a.minusDays(1)), k(1, b).copy(id = 1))).map { it.id })
        assertEquals(listOf(1L, 2L), BescheidAnsicht.fuerRechnung(listOf(k(2, a), k(1, a))).map { it.id })
    }

    @Test fun `SR-9f0a44b25efd AC3 fehlendes Dokument bleibt in der Liste und ist nicht oeffenbar`() {
        val l = BescheidAnsicht.fuerRechnung(listOf(k(1, LocalDate.of(2026, 5, 1), null)))
        assertEquals(1, l.size)
        assertFalse(BescheidAnsicht.kannOeffnen(l[0]))
    }
}
