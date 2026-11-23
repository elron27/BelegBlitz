package de.belegblitz.domain

import de.belegblitz.domain.EinreichungStatus.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RechnungsFilterTest {
    private fun z(rechnung: Long, kasse: Kasse, status: EinreichungStatus, erst: Long? = null, jahr: Int = 2026) =
        AuswertungsZeile(1, jahr, kasse, status, 1000, erst, rechnung)

    private val zeilen = listOf(
        z(1, Kasse.DAK, ERFASST),
        z(2, Kasse.DAK, EINGEREICHT),
        z(3, Kasse.DAK, ERSTATTET, 1000),
        z(4, Kasse.DAK, TEILWEISE_ERSTATTET, 400),
        z(5, Kasse.DAK, ABGELEHNT, 0),
        z(6, Kasse.VKB, EINGEREICHT),
        z(7, Kasse.DAK, EINGEREICHT, jahr = 2025),
    )

    private fun ids(g: StatusGruppe, kasse: Kasse = Kasse.DAK, jahr: Int? = 2026) =
        Auswertung.rechnungIds(zeilen, RechnungsFilter(kasse, jahr, g))

    @Test fun `SR-75f859f5adc5 AC1 Status-Gruppen je Kasse und Jahr`() {
        assertEquals(setOf(2L, 3L, 4L, 5L), ids(StatusGruppe.EINGEREICHT))
        assertEquals(setOf(3L, 4L), ids(StatusGruppe.ERSTATTET))
        assertEquals(setOf(2L, 4L), ids(StatusGruppe.OFFEN))
        assertEquals(setOf(4L, 5L), ids(StatusGruppe.NICHT_ERSTATTET))
        assertEquals(setOf(6L), ids(StatusGruppe.OFFEN, Kasse.VKB))
    }

    @Test fun `SR-75f859f5adc5 AC1 ohne Jahr zaehlen alle Jahre`() =
        assertEquals(setOf(2L, 4L, 7L), ids(StatusGruppe.OFFEN, jahr = null))

    @Test fun `SR-75f859f5adc5 AC2 Rechnung passt wenn mindestens eine Einreichung passt`() {
        val beide = listOf(z(9, Kasse.DAK, ERSTATTET, 1000), z(9, Kasse.VKB, EINGEREICHT))
        assertEquals(setOf(9L), Auswertung.rechnungIds(beide, RechnungsFilter(Kasse.VKB, 2026, StatusGruppe.OFFEN)))
        assertEquals(setOf(9L), Auswertung.rechnungIds(beide, RechnungsFilter(Kasse.DAK, 2026, StatusGruppe.ERSTATTET)))
        assertTrue(Auswertung.rechnungIds(beide, RechnungsFilter(Kasse.DAK, 2026, StatusGruppe.OFFEN)).isEmpty())
    }

    @Test fun `SR-75f859f5adc5 AC3 Filter ist ein Wert und kann entfernt werden`() {
        val aktiv: RechnungsFilter? = RechnungsFilter(Kasse.DAK, 2026, StatusGruppe.OFFEN)
        val entfernt: RechnungsFilter? = null
        assertFalse(aktiv == null)
        assertEquals(null, entfernt)
        assertEquals(RechnungsFilter(Kasse.DAK, 2026, StatusGruppe.OFFEN), aktiv)
    }
}
