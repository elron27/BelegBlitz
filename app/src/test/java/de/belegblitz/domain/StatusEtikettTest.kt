package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** SR-17cd13ad9ebd: Status-Etiketten und Fortschrittsanteil. */
class StatusEtikettTest {
    @Test fun `SR-17cd13ad9ebd Kriterium 1 jeder Status hat eine eigene Etikett-Art`() {
        val arten = EinreichungStatus.entries.map { StatusEtikett.art(it) }
        assertEquals(EinreichungStatus.entries.size, arten.toSet().size)
        assertEquals(EtikettArt.TEILWEISE, StatusEtikett.art(EinreichungStatus.TEILWEISE_ERSTATTET))
        assertEquals(EtikettArt.ABGELEHNT, StatusEtikett.art(EinreichungStatus.ABGELEHNT))
    }

    @Test fun `SR-17cd13ad9ebd Kriterium 2 Fortschritt ist erstattet durch eingereicht und 0 ohne Einreichung`() {
        assertEquals(0.5f, StatusEtikett.fortschritt(5_000, 10_000), 0.0001f)
        assertEquals(0f, StatusEtikett.fortschritt(5_000, 0), 0f)
        assertEquals(0f, StatusEtikett.fortschritt(0, 10_000), 0f)
    }

    @Test fun `SR-17cd13ad9ebd Kriterium 3 Fortschritt wird auf 0 bis 1 begrenzt`() {
        assertEquals(1f, StatusEtikett.fortschritt(20_000, 10_000), 0f)
        assertEquals(0f, StatusEtikett.fortschritt(-100, 10_000), 0f)
        assertEquals(0f, StatusEtikett.fortschritt(100, -5), 0f)
    }
}
