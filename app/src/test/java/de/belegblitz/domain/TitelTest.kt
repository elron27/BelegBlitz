package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TitelTest {
    @Test fun `SR-f5771d66f4cc Kriterium 1 Titel Beispiel Zahnarzt MVZ`() =
        assertEquals("Zahnarzt_MVZ-Chiemgau-lacht_20260728",
            Titel.erzeugen("Zahnarzt", "MVZ Chiemgau lacht", LocalDate.of(2026, 7, 28)))

    @Test fun `SR-f5771d66f4cc Kriterium 3 Titel Umlaute und Sonderzeichen dateinamensicher`() =
        assertEquals("Hals-Nasen-Ohren_Dr-Mueller-Strasse_20260105",
            Titel.erzeugen("Hals-Nasen-Ohren", "Dr. Müller / Straße?", LocalDate.of(2026, 1, 5)))
}