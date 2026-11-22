package de.belegblitz.domain

import de.belegblitz.domain.matching.BescheidPlanung
import de.belegblitz.domain.matching.RechnungMerkmale
import de.belegblitz.domain.ocr.BescheidErgebnis
import de.belegblitz.domain.ocr.BescheidZeile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EinreichungsregelnTest {
    private val heute = LocalDate.of(2026, 11, 22)
    private val d1 = LocalDate.of(2026, 7, 28)
    private val d2 = LocalDate.of(2026, 8, 2)

    // SR-7d1f8913431f
    @Test fun `SR-7d1f8913431f AC2 alle fuenf Status vorhanden`() {
        assertEquals(
            listOf("ERFASST", "EINGEREICHT", "ERSTATTET", "TEILWEISE_ERSTATTET", "ABGELEHNT"),
            EinreichungStatus.entries.map { it.name },
        )
        assertEquals(setOf("DAK", "VKB"), Kasse.entries.map { it.name }.toSet())
    }

    // SR-9b67f3ef174c
    @Test fun `SR-9b67f3ef174c AC1 ein Tipp setzt Eingereicht`() {
        assertTrue(Einreichungsregeln.kannAlsEingereichtMarkiert(EinreichungStatus.ERFASST))
        assertFalse(Einreichungsregeln.kannAlsEingereichtMarkiert(EinreichungStatus.ERSTATTET))
        assertEquals(EinreichungStatus.EINGEREICHT, Einreichungsregeln.alsEingereicht(heute).first)
    }

    @Test fun `SR-9b67f3ef174c AC2 heutiges Datum vorbelegt und aenderbar`() {
        assertEquals(heute, Einreichungsregeln.alsEingereicht(heute).second)
        assertEquals(d1, Einreichungsregeln.alsEingereicht(heute, d1).second)
    }

    // SR-93cb9591c1a1
    @Test fun `SR-93cb9591c1a1 AC1 Bescheid wird in Positionen je Rechnung aufgeteilt`() {
        val rechnungen = listOf(
            RechnungMerkmale(1, d1, "Zahnarzt", 14000),
            RechnungMerkmale(2, d2, "Zahnarzt", 10000),
        )
        val bescheid = BescheidErgebnis(
            kasse = null, schreibdatum = null, erstattungGesamtCent = null, hinweise = emptyList(), datenSeite = 0,
            positionen = listOf(
                BescheidZeile(d1, 14000, 4000, "Zahnarzt"),
                BescheidZeile(d2, 10000, 3000, "Zahnarzt"),
            ),
        )
        val plan = BescheidPlanung.planen(bescheid, rechnungen)
        assertEquals(listOf<Long?>(1L, 2L), plan.map { it.rechnungId })
        assertEquals(listOf<Long?>(4000L, 3000L), plan.map { it.zeile.erstattungCent })
    }

    @Test fun `SR-93cb9591c1a1 AC2 Rechnung erhaelt Betraege aus mehreren Bescheiden`() {
        // 100 EUR eingereicht, Bescheid 1: 40 EUR, Bescheid 2: 60 EUR -> zusammen erstattet
        val summen = BescheidPlanung.jeRechnung(listOf(1L to 4000L, 1L to 6000L, 2L to 500L))
        assertEquals(10000L, summen[1L])
        assertEquals(500L, summen[2L])
        val stand = Einreichungsregeln.stand(10000, listOf(4000, 6000))
        assertEquals(EinreichungStatus.ERSTATTET, stand.status)
        assertEquals(10000L, stand.erstattetCent)
        assertEquals(EinreichungStatus.TEILWEISE_ERSTATTET, Einreichungsregeln.stand(10000, listOf(4000)).status)
    }

    // SR-40de3efa693e wird zusaetzlich ueber Einreichungsregeln.stand angewendet
    @Test fun `SR-40de3efa693e AC1 ohne Positionen oder mit Null ist Abgelehnt`() {
        assertEquals(EinreichungStatus.ABGELEHNT, Einreichungsregeln.stand(10000, listOf(0)).status)
    }

    // SR-a6fc7a6cdbe1
    @Test fun `SR-a6fc7a6cdbe1 AC1 Hinweis wird an vorhandenen angehaengt ohne Doppelung`() {
        assertEquals("0240: Kuerzung", Einreichungsregeln.hinweisZusammenfuehren(null, "0240: Kuerzung"))
        assertEquals("0240: Kuerzung", Einreichungsregeln.hinweisZusammenfuehren("0240: Kuerzung", "0240: Kuerzung"))
        assertEquals("A\n0899: B", Einreichungsregeln.hinweisZusammenfuehren("A", "0899: B"))
        assertEquals("A", Einreichungsregeln.hinweisZusammenfuehren("A", null))
        assertNull(Einreichungsregeln.hinweisZusammenfuehren("  ", null))
    }

    @Test fun `SR-a6fc7a6cdbe1 AC2 Nutzer kann Hinweis selbst eintragen`() =
        assertEquals("Eigener Text", Einreichungsregeln.hinweisZusammenfuehren(null, "  Eigener Text "))

    // SR-d2f03abf6d13
    @Test fun `SR-d2f03abf6d13 AC1 VKB wird bei abgelehnt oder teilweise angeboten`() {
        assertTrue(Einreichungsregeln.vkbAnbieten(EinreichungStatus.ABGELEHNT))
        assertTrue(Einreichungsregeln.vkbAnbieten(EinreichungStatus.TEILWEISE_ERSTATTET))
        assertFalse(Einreichungsregeln.vkbAnbieten(EinreichungStatus.ERSTATTET))
        assertFalse(Einreichungsregeln.vkbAnbieten(EinreichungStatus.EINGEREICHT))
        assertNull(Einreichungsregeln.vkbRestbetrag(EinreichungStatus.ERSTATTET, 10000, 10000))
    }

    @Test fun `SR-d2f03abf6d13 AC2 Restbetrag ist der nicht erstattete Teil`() {
        assertEquals(6000L, Einreichungsregeln.vkbRestbetrag(EinreichungStatus.TEILWEISE_ERSTATTET, 10000, 4000))
        assertEquals(10000L, Einreichungsregeln.vkbRestbetrag(EinreichungStatus.ABGELEHNT, 10000, 0))
        assertEquals(10000L, Einreichungsregeln.vkbRestbetrag(EinreichungStatus.ABGELEHNT, 10000, null))
    }

    // SR-e268ffbc5098
    @Test fun `SR-e268ffbc5098 AC1 unbestaetigte unsichere Felder bleiben markiert`() {
        val v = RechnungVorschlag(unsicher = setOf(VorschlagFeld.DATUM, VorschlagFeld.BETRAG))
        assertEquals(setOf(VorschlagFeld.DATUM, VorschlagFeld.BETRAG), Unsicherheit.offen(v, emptySet()))
        assertEquals(setOf(VorschlagFeld.BETRAG), Unsicherheit.offen(v, setOf(VorschlagFeld.DATUM)))
        assertTrue(Unsicherheit.offen(null, emptySet()).isEmpty())
    }

    @Test fun `SR-e268ffbc5098 AC2 jeder Wert ist vor dem Speichern korrigierbar`() {
        // Pruefung akzeptiert beliebige vom Nutzer korrigierte Werte
        val e = RechnungEingabe.pruefen("Praxis Neu", "Hausarzt", "03.08.2026", "12,50")
        assertEquals(1250L, e?.betragCent)
        assertEquals(LocalDate.of(2026, 8, 3), e?.datum)
    }

    // SR-a6fc7a6cdbe1 Kriterium 2: mehrere Codes im selbst eingetragenen Hinweis bleiben vollständig erhalten
    @Test fun `SR-a6fc7a6cdbe1 mehrere Hinweiscodes bleiben erhalten`() {
        assertEquals("0240 0899", Einreichungsregeln.hinweisZusammenfuehren(null, " 0240 0899 "))
        assertEquals("0240 0899\n0777",Einreichungsregeln.hinweisZusammenfuehren("0240 0899", "0777"))
    }
}
