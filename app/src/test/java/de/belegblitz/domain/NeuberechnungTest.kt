package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Issue #16: Neuberechnung einer Einreichung nach Bearbeiten/Löschen von Rechnung und Bescheid. */
class NeuberechnungTest {
    private val datum = LocalDate.of(2026, 3, 1)

    @Test fun `Issue 16 Betrag eines Bescheid-Eintrags geaendert - Summe und Status folgen`() {
        val s = Einreichungsregeln.neuBerechnen(10000, datum, EinreichungStatus.TEILWEISE_ERSTATTET, 6000, listOf(10000))
        assertEquals(10000L, s.erstattetCent)
        assertEquals(EinreichungStatus.ERSTATTET, s.status)
    }

    @Test fun `Issue 16 Positionen verbleiben - Summe aller Positionen zaehlt`() {
        val s = Einreichungsregeln.neuBerechnen(10000, datum, EinreichungStatus.ERSTATTET, 10000, listOf(3000, 2000), listOf(5000))
        assertEquals(5000L, s.erstattetCent)
        assertEquals(EinreichungStatus.TEILWEISE_ERSTATTET, s.status)
    }

    @Test fun `Issue 16 Bescheid geloescht ohne Positionen - Rueckfall auf Eingereicht mit Einreichungsdatum`() {
        val s = Einreichungsregeln.neuBerechnen(10000, datum, EinreichungStatus.ERSTATTET, 10000, emptyList(), listOf(10000))
        assertNull(s.erstattetCent)
        assertEquals(EinreichungStatus.EINGEREICHT, s.status)
    }

    @Test fun `Issue 16 Bescheid geloescht ohne Positionen und ohne Einreichungsdatum - Rueckfall auf Erfasst`() {
        val s = Einreichungsregeln.neuBerechnen(10000, null, EinreichungStatus.ABGELEHNT, 0, emptyList(), listOf(0))
        assertNull(s.erstattetCent)
        assertEquals(EinreichungStatus.ERFASST, s.status)
    }

    @Test fun `Issue 16 manuelle Erstattung bleibt beim Loeschen eines Bescheids erhalten`() {
        // Erstattung 4000 stimmt nicht mit der Summe der gelöschten Positionen (7000) überein -> von Hand eingetragen
        val s = Einreichungsregeln.neuBerechnen(10000, datum, EinreichungStatus.TEILWEISE_ERSTATTET, 4000, emptyList(), listOf(7000))
        assertEquals(4000L, s.erstattetCent)
        assertEquals(EinreichungStatus.TEILWEISE_ERSTATTET, s.status)
    }

    @Test fun `Issue 16 Kassenwechsel - alte Einreichung faellt zurueck, neue erhaelt die Erstattung`() {
        val alt = Einreichungsregeln.neuBerechnen(8000, datum, EinreichungStatus.ERSTATTET, 8000, emptyList(), listOf(8000))
        assertNull(alt.erstattetCent)
        assertEquals(EinreichungStatus.EINGEREICHT, alt.status)
        // neue Einreichung der anderen Kasse (frisch angelegt, Erfasst, keine Erstattung)
        val neu = Einreichungsregeln.neuBerechnen(8000, null, EinreichungStatus.ERFASST, null, listOf(8000))
        assertEquals(8000L, neu.erstattetCent)
        assertEquals(EinreichungStatus.ERSTATTET, neu.status)
    }

    @Test fun `Issue 16 Kassenwechsel mit weiteren Positionen - alte Einreichung behaelt Rest`() {
        val alt = Einreichungsregeln.neuBerechnen(10000, datum, EinreichungStatus.ERSTATTET, 10000, listOf(6000), listOf(4000))
        assertEquals(6000L, alt.erstattetCent)
        assertEquals(EinreichungStatus.TEILWEISE_ERSTATTET, alt.status)
    }

    @Test fun `Issue 16 Betragsaenderung der Rechnung mit Positionen - Status aus neuem Betrag`() {
        // 10000 erstattet; Rechnung wird auf 12000 erhöht -> nur teilweise
        val mehr = Einreichungsregeln.neuBerechnen(12000, datum, EinreichungStatus.ERSTATTET, 10000, listOf(10000))
        assertEquals(EinreichungStatus.TEILWEISE_ERSTATTET, mehr.status)
        // Rechnung wird auf 9000 gesenkt -> voll erstattet
        val weniger = Einreichungsregeln.neuBerechnen(9000, datum, EinreichungStatus.TEILWEISE_ERSTATTET, 8000, listOf(9000))
        assertEquals(EinreichungStatus.ERSTATTET, weniger.status)
    }

    @Test fun `Issue 16 Betragsaenderung der Rechnung ohne Positionen - manuelle Erstattung bleibt, Status folgt dem Betrag`() {
        val s = Einreichungsregeln.neuBerechnen(12000, datum, EinreichungStatus.ERSTATTET, 10000, emptyList())
        assertEquals(10000L, s.erstattetCent)
        assertEquals(EinreichungStatus.TEILWEISE_ERSTATTET, s.status)
    }

    @Test fun `Issue 16 Betragsaenderung ohne Erstattung - Status bleibt`() {
        val s = Einreichungsregeln.neuBerechnen(12000, datum, EinreichungStatus.EINGEREICHT, null, emptyList())
        assertNull(s.erstattetCent)
        assertEquals(EinreichungStatus.EINGEREICHT, s.status)
    }

    @Test fun `Issue 16 Titel wird nach Bearbeiten neu abgeleitet`() {
        val e = RechnungEingabe.pruefen("Dr. Müller", "Zahnarzt", "05.04.2026", "120,00")!!
        assertEquals("Zahnarzt_Dr-Mueller_20260405", e.titel)
        assertEquals(12000L, e.betragCent)
    }
}
