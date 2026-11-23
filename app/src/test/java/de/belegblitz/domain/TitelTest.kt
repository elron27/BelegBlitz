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

    @Test fun `SR-010571e2f9d9 Kriterium 1 Anzeige bricht nur nach Unterstrich und Bindestrich um`() {
        val z = "​"
        assertEquals("Zahnarzt_${z}MVZ-${z}Chiemgau-${z}lacht_${z}20260728",
            Titel.fuerAnzeige("Zahnarzt_MVZ-Chiemgau-lacht_20260728"))
    }

    @Test fun `SR-010571e2f9d9 Kriterium 1 Anzeige ohne Trennzeichen bleibt unveraendert`() =
        assertEquals("Wort", Titel.fuerAnzeige("Wort"))

    @Test fun `SR-010571e2f9d9 Kriterium 1 Anzeige entfernt ohne Zero-Width-Space den Originaltitel wieder`() {
        val t = "Hals-Nasen-Ohren_Dr-Mueller_20260105"
        assertEquals(t, Titel.fuerAnzeige(t).replace("​", ""))
    }
}
