package de.belegblitz.domain

import de.belegblitz.domain.EinreichungStatus.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AuswertungTest {
    private fun z(kasse: Kasse, status: EinreichungStatus, ein: Long, erst: Long? = null, jahr: Int = 2026, person: Long = 1) =
        AuswertungsZeile(person, jahr, kasse, status, ein, erst)

    private val zeilen = listOf(
        z(Kasse.DAK, EINGEREICHT, 10000),
        z(Kasse.DAK, ERSTATTET, 20000, 20000),
        z(Kasse.DAK, TEILWEISE_ERSTATTET, 30000, 10000),
        z(Kasse.DAK, ERFASST, 5000),
        z(Kasse.DAK, ABGELEHNT, 4000, 0),
        z(Kasse.VKB, TEILWEISE_ERSTATTET, 20000, 5000, jahr = 2025, person = 2),
    )

    @Test fun `SR-2b75d02ae8a8 AC1 Summen eingereicht erstattet offen je Kasse`() {
        val dak = Auswertung.jeKasse(zeilen).getValue(Kasse.DAK)
        assertEquals(64000L, dak.eingereichtCent) // ohne ERFASST
        assertEquals(30000L, dak.erstattetCent)
        assertEquals(10000L + 20000L, dak.offenCent) // 10000 offen + Rest 20000 der Teilerstattung
        val vkb = Auswertung.jeKasse(zeilen).getValue(Kasse.VKB)
        assertEquals(Kassensumme(20000, 5000, 15000), vkb)
    }

    @Test fun `SR-2b75d02ae8a8 AC1 beide Kassen sind auch ohne Einreichungen vorhanden`() =
        assertEquals(Kassensumme(0, 0, 0), Auswertung.jeKasse(emptyList()).getValue(Kasse.VKB))

    @Test fun `SR-2b75d02ae8a8 AC2 nach Aenderung einer Einreichung stimmen die Summen neu`() {
        val vorher = Auswertung.summe(listOf(z(Kasse.DAK, EINGEREICHT, 10000)))
        assertEquals(10000L, vorher.offenCent)
        val nachher = Auswertung.summe(listOf(z(Kasse.DAK, ERSTATTET, 10000, 10000)))
        assertEquals(Kassensumme(10000, 10000, 0), nachher)
    }

    @Test fun `SR-2b75d02ae8a8 Betrag aendern passt den Status an die Erstattung an`() {
        assertEquals(ERSTATTET, Einreichungsregeln.statusNachBetragsaenderung(TEILWEISE_ERSTATTET, 10000, 10000))
        assertEquals(TEILWEISE_ERSTATTET, Einreichungsregeln.statusNachBetragsaenderung(ERSTATTET, 20000, 10000))
        assertEquals(EINGEREICHT, Einreichungsregeln.statusNachBetragsaenderung(EINGEREICHT, 20000, null))
    }

    @Test fun `SR-d109d26f3f86 AC1 Filter nach Jahr und Kasse`() {
        assertEquals(1, Auswertung.filtern(zeilen, 2025, null, null).size)
        assertEquals(5, Auswertung.filtern(zeilen, null, Kasse.DAK, null).size)
        assertEquals(0, Auswertung.filtern(zeilen, 2025, Kasse.DAK, null).size)
        assertEquals(6, Auswertung.filtern(zeilen, null, null, null).size)
        assertEquals(listOf(2026, 2025), Auswertung.jahre(zeilen))
    }

    @Test fun `SR-d109d26f3f86 AC2 Personenfilter erst bei mehreren Personen`() {
        assertFalse(Auswertung.personenFilterSichtbar(1))
        assertTrue(Auswertung.personenFilterSichtbar(2))
        assertEquals(1, Auswertung.filtern(zeilen, null, null, 2).size)
    }

    private val verlauf = listOf(
        BeitragsEintrag(LocalDate.of(2025, 1, 1), 40000),
        BeitragsEintrag(LocalDate.of(2026, 3, 1), 45000),
    )

    @Test fun `SR-5b4224df66fd AC1 Bilanz summiert Monatsbeitraege und VKB-Erstattungen`() {
        val b = VkbJahresbilanz.berechnen(2025, verlauf, 300000, LocalDate.of(2026, 5, 10))
        assertEquals(12 * 40000L, b.beitraegeCent)
        assertEquals(300000L, b.erstattetCent)
        assertEquals(12, b.monate)
        assertFalse(b.laufendesJahr)
    }

    @Test fun `SR-5b4224df66fd AC2 Differenz und Hereingeholt`() {
        val nein = VkbJahresbilanz.berechnen(2025, verlauf, 300000, LocalDate.of(2026, 5, 10))
        assertEquals(300000L - 480000L, nein.differenzCent)
        assertFalse(nein.hereingeholt)
        val ja = VkbJahresbilanz.berechnen(2025, verlauf, 500000, LocalDate.of(2026, 5, 10))
        assertEquals(20000L, ja.differenzCent)
        assertTrue(ja.hereingeholt)
    }

    @Test fun `SR-5b4224df66fd laufendes Jahr zaehlt nur bis zum aktuellen Monat`() {
        val b = VkbJahresbilanz.berechnen(2026, verlauf, 0, LocalDate.of(2026, 4, 15))
        assertEquals(2 * 40000L + 2 * 45000L, b.beitraegeCent) // Jan, Feb alt; Mar, Apr neu
        assertEquals(4, b.monate)
        assertTrue(b.laufendesJahr)
    }

    @Test fun `SR-5b4224df66fd Monate ohne bekannten Beitrag zaehlen nicht`() =
        assertEquals(0L, VkbJahresbilanz.berechnen(2024, verlauf, 0, LocalDate.of(2026, 5, 10)).beitraegeCent)
}
