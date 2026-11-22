package de.belegblitz.domain

import de.belegblitz.domain.update.RemoteRelease
import de.belegblitz.domain.update.UpdateRegeln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Stufe2Test {
    // --- SR-c43e8d9ab0a8 App-Sperre ---
    @Test fun `SR-c43e8d9ab0a8 AC1 ausgeschaltete Sperre sperrt nie`() {
        assertFalse(Sperre.beimStartGesperrt(false))
        assertFalse(Sperre.beimZurueckkehrenGesperrt(false, 0L, 10_000_000L))
    }

    @Test fun `SR-c43e8d9ab0a8 AC1 Sperre laesst sich nur mit eingerichteter Authentifizierung einschalten`() {
        assertTrue(Sperre.einschaltenMoeglich(true))
        assertFalse(Sperre.einschaltenMoeglich(false))
    }

    @Test fun `SR-c43e8d9ab0a8 AC2 eingeschaltete Sperre sperrt beim Start`() {
        assertTrue(Sperre.beimStartGesperrt(true))
    }

    @Test fun `SR-c43e8d9ab0a8 AC2 nach kurzer Frist im Hintergrund wird wieder gesperrt`() {
        assertFalse(Sperre.beimZurueckkehrenGesperrt(true, 1_000L, 1_000L + Sperre.FRIST_MS - 1))
        assertTrue(Sperre.beimZurueckkehrenGesperrt(true, 1_000L, 1_000L + Sperre.FRIST_MS))
        assertFalse(Sperre.beimZurueckkehrenGesperrt(true, null, 9_999_999L))
    }

    // --- SR-aada699191a1 Erinnerung ---
    private fun e(id: Long, datum: LocalDate?, status: EinreichungStatus = EinreichungStatus.EINGEREICHT) =
        EinreichungSicht(id, datum, status)

    @Test fun `SR-aada699191a1 AC1 Vorgabe der Wartezeit ist 4 Wochen und Eingabe wird begrenzt`() {
        assertEquals(4, Erinnerung.VORGABE_WOCHEN)
        assertEquals(1, Erinnerung.begrenzeWochen(0))
        assertEquals(52, Erinnerung.begrenzeWochen(500))
        assertEquals(6, Erinnerung.begrenzeWochen(6))
    }

    @Test fun `SR-aada699191a1 AC2 nach Ablauf der Wartezeit wird erinnert`() {
        val eingereicht = LocalDate.of(2026, 10, 1)
        val liste = listOf(e(1, eingereicht))
        assertTrue(Erinnerung.faellige(liste, eingereicht.plusWeeks(4).minusDays(1), 4).isEmpty())
        assertEquals(listOf(1L), Erinnerung.faellige(liste, eingereicht.plusWeeks(4), 4).map { it.id })
        // kürzere Wartezeit erinnert früher
        assertEquals(1, Erinnerung.faellige(liste, eingereicht.plusWeeks(2), 2).size)
    }

    @Test fun `SR-aada699191a1 AC2 Einreichungen mit Bescheid oder ohne Datum werden nicht erinnert`() {
        val alt = LocalDate.of(2026, 1, 1)
        val liste = listOf(
            e(1, alt, EinreichungStatus.ERSTATTET),
            e(2, alt, EinreichungStatus.ABGELEHNT),
            e(3, alt, EinreichungStatus.TEILWEISE_ERSTATTET),
            e(4, alt, EinreichungStatus.ERFASST),
            e(5, null),
            e(6, alt),
        )
        assertEquals(listOf(6L), Erinnerung.faellige(liste, LocalDate.of(2026, 11, 22), 4).map { it.id })
    }

    @Test fun `SR-aada699191a1 AC3 keine Fristen-Erinnerung, nur Wartezeit seit Einreichung`() {
        // Die Logik kennt nur Einreichungsdatum und Wartezeit; ein Einreichungsdatum in der Zukunft erinnert nicht.
        val heute = LocalDate.of(2026, 11, 22)
        assertTrue(Erinnerung.faellige(listOf(e(1, heute.plusDays(1))), heute, 1).isEmpty())
        assertTrue(Erinnerung.faellige(listOf(e(1, heute)), heute, 4).isEmpty())
    }

    // --- App-Update (wie FotoSortierer U-1) ---
    private fun rel(code: Int) = RemoteRelease(code, "1.0", "BelegBlitz.apk", "ab")

    @Test fun `Update nur bei hoeherem versionCode angeboten`() {
        assertTrue(UpdateRegeln.istNeuer(1, rel(2)))
        assertFalse(UpdateRegeln.istNeuer(2, rel(2)))
        assertFalse(UpdateRegeln.istNeuer(3, rel(2)))
    }

    @Test fun `Update APK-Name darf kein Pfad sein`() {
        assertTrue(UpdateRegeln.istSicherAPKName("BelegBlitz-1.0.apk"))
        assertFalse(UpdateRegeln.istSicherAPKName("../x.apk"))
        assertFalse(UpdateRegeln.istSicherAPKName("a\\b.apk"))
        assertFalse(UpdateRegeln.istSicherAPKName("datei.txt"))
    }

    @Test fun `Update SHA-256 als Hex`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateRegeln.sha256Hex("abc".toByteArray()))
    }
}
