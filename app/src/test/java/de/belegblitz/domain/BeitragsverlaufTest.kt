package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BeitragsverlaufTest {
    private val verlauf = listOf(
        BeitragsEintrag(LocalDate.of(2025, 1, 1), 40000),
        BeitragsEintrag(LocalDate.of(2026, 3, 1), 45000),
    )

    @Test fun `SR-984a2c74ebc1 AC1 Eintrag mit Betrag und Gueltig-ab-Datum liefert den Beitrag`() =
        assertEquals(40000L, Beitragsverlauf.fuerMonat(verlauf.take(1), YearMonth.of(2025, 6)))

    @Test fun `SR-984a2c74ebc1 AC2 mehrere Eintraege ergeben Beitrag je Monat`() {
        assertEquals(40000L, Beitragsverlauf.fuerMonat(verlauf, YearMonth.of(2026, 2)))
        assertEquals(45000L, Beitragsverlauf.fuerMonat(verlauf, YearMonth.of(2026, 3)))
        assertEquals(45000L, Beitragsverlauf.fuerMonat(verlauf.reversed(), YearMonth.of(2027, 1)))
    }

    @Test fun `SR-984a2c74ebc1 AC2 vor dem ersten Eintrag ist der Beitrag unbekannt`() =
        assertNull(Beitragsverlauf.fuerMonat(verlauf, YearMonth.of(2024, 12)))

    @Test fun `SR-984a2c74ebc1 Eintrag mitten im Monat gilt fuer diesen Monat`() =
        assertEquals(45000L, Beitragsverlauf.fuerMonat(
            listOf(BeitragsEintrag(LocalDate.of(2026, 3, 15), 45000)), YearMonth.of(2026, 3)))
}