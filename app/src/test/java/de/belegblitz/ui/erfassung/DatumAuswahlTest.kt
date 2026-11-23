package de.belegblitz.ui.erfassung

import de.belegblitz.domain.RechnungEingabe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DatumAuswahlTest {
    // SR-306dc2, Kriterium 2: Kalenderauswahl (UTC-Millisekunden des DatePickers) liefert TT.MM.JJJJ, vom Prüfbildschirm akzeptiert
    @Test fun `SR-306dc2 Kriterium 2 Kalenderauswahl liefert gueltiges Format`() {
        val ms = LocalDate.of(2026, 7, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val text = datumText(ms)
        assertEquals("08.07.2026", text)
        assertNotNull(RechnungEingabe.pruefen("Praxis", "Zahnarzt", text, "10,00"))
    }
}
