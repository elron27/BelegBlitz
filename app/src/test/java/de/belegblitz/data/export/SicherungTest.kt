package de.belegblitz.data.export

import de.belegblitz.data.Bescheid
import de.belegblitz.data.BescheidPosition
import de.belegblitz.data.Einreichung
import de.belegblitz.data.Person
import de.belegblitz.data.Rechnung
import de.belegblitz.data.VkbBeitrag
import de.belegblitz.domain.EinreichungStatus
import de.belegblitz.domain.Kasse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class SicherungTest {
    private fun beispiel(): Sicherung {
        val r1 = Rechnung(1, 1, "Dr. Müller", "Hausarzt", LocalDate.of(2026, 3, 5), 12_345, "Hausarzt_DrMueller_20260305", "Hausarzt_DrMueller_20260305_ab12cd34.pdf", "OCR\nText \"x\"", 111)
        val r2 = Rechnung(2, 1, "Praxis/Neu: Kind?", "Zahnarzt", LocalDate.of(2025, 12, 30), 9_900, "Zahnarzt_Praxis/Neu: Kind?_20251230", "Zahnarzt_x.pdf", null, 222)
        val r3 = Rechnung(3, 1, "Ohne", "Orthopäde", LocalDate.of(2026, 1, 2), 500, "Orthopaede_Ohne_20260102", null, null, 333)
        return Sicherung(
            personen = listOf(Person(1, "Stefan")),
            rechnungen = listOf(r1, r2, r3),
            einreichungen = listOf(
                Einreichung(1, 1, Kasse.DAK, LocalDate.of(2026, 3, 10), 12_345, EinreichungStatus.TEILWEISE_ERSTATTET, 8_000, "Hinweis ä", r1.pdfPfad),
                Einreichung(2, 1, Kasse.VKB, null, 4_345, EinreichungStatus.ERFASST, null, null, r1.pdfPfad),
                Einreichung(3, 2, Kasse.DAK, LocalDate.of(2026, 1, 1), 9_900, EinreichungStatus.ABGELEHNT, 0, null, r2.pdfPfad),
            ),
            bescheide = listOf(
                Bescheid(1, Kasse.DAK, LocalDate.of(2026, 4, 1), "Bescheid_DAK_x.pdf", "Bescheidtext"),
                Bescheid(2, Kasse.DAK, LocalDate.of(2026, 4, 1), "Bescheid_DAK_y.pdf", null),
            ),
            positionen = listOf(BescheidPosition(1, 1, 1, 1, 8_000), BescheidPosition(2, 1, 2, null, 0)),
            beitraege = listOf(VkbBeitrag(1, LocalDate.of(2026, 1, 1), 35_000)),
            einstellungen = mapOf(Sicherung.EINST_WARTEZEIT to "6", Sicherung.EINST_SPERRE to "true"),
        )
    }

    // --- SR-e334c167571f: Export ---
    @Test fun `SR-e334c167571f AC1 Dokumente liegen in Kasse Jahr Dokumente mit Titel als Dateiname`() {
        val (export, kopien) = SicherungsPlaner.exportieren(beispiel())
        val pfade = kopien.map { it.relPfad }.toSet()
        assertTrue(pfade.toString(), "DAK/2026/Dokumente/Hausarzt_DrMueller_20260305.pdf" in pfade)
        assertTrue("VKB/2026/Dokumente/Hausarzt_DrMueller_20260305.pdf" in pfade)
        assertTrue("DAK/2025/Dokumente/Zahnarzt_Praxis_Neu_ Kind__20251230.pdf" in pfade) // unzulässige Zeichen ersetzt
        assertTrue("DAK/2026/Dokumente/Bescheid_DAK_2026-04-01.pdf" in pfade)
        assertTrue("DAK/2026/Dokumente/Bescheid_DAK_2026-04-01_2.pdf" in pfade) // Namensgleichheit eindeutig gemacht
        assertEquals("DAK/2026/Dokumente/Hausarzt_DrMueller_20260305.pdf", export.einreichungen[0].dokumentPfad)
        assertEquals("VKB/2026/Dokumente/Hausarzt_DrMueller_20260305.pdf", export.einreichungen[1].dokumentPfad)
        assertNull(export.rechnungen[2].pdfPfad)
    }

    @Test fun `SR-e334c167571f AC1 Rechnung ohne Einreichung landet im Ordner Ohne_Kasse`() {
        val s = beispiel().let { it.copy(rechnungen = it.rechnungen.map { r -> if (r.id == 3L) r.copy(pdfPfad = "o.pdf") else r }) }
        val (_, kopien) = SicherungsPlaner.exportieren(s)
        assertTrue(kopien.any { it.relPfad == "Ohne_Kasse/2026/Dokumente/Orthopaede_Ohne_20260102.pdf" })
    }

    @Test fun `SR-e334c167571f AC2 Datendatei enthaelt alle Einreichungen und Erstattungen`() {
        val (export, _) = SicherungsPlaner.exportieren(beispiel())
        val json = SicherungJson.zuJson(export)
        val zurueck = SicherungJson.ausJson(json)
        assertEquals(3, zurueck.einreichungen.size)
        assertEquals(listOf<Long?>(8_000L, null, 0L), zurueck.einreichungen.map { it.erstatteterBetragCent })
        assertEquals(2, zurueck.positionen.size)
        assertTrue(json.contains("\"formatVersion\": 1"))
    }

    @Test fun `SR-e334c167571f AC3 Zielordner kommt aus der Systemauswahl OpenDocumentTree`() {
        val q = java.io.File("src/main/java/de/belegblitz/ui/settings/SettingsScreen.kt").readText()
        assertTrue(q.contains("ActivityResultContracts.OpenDocumentTree()"))
        assertTrue(q.contains("vm.exportieren(uri)"))
    }

    // --- SR-176f6f1f60df: Wiederherstellung ---
    @Test fun `SR-176f6f1f60df AC1 leere App erhaelt aus Datendatei und Ordnerstruktur alle Daten zurueck`() {
        val app = beispiel()
        val (export, kopien) = SicherungsPlaner.exportieren(app)
        val gelesen = SicherungJson.ausJson(SicherungJson.zuJson(export))
        val (wieder, quellen) = SicherungsPlaner.wiederherstellen(gelesen)
        assertEquals(app.personen, wieder.personen)
        assertEquals(app.beitraege, wieder.beitraege)
        assertEquals(app.einstellungen, wieder.einstellungen)
        assertEquals(app.positionen, wieder.positionen)
        assertEquals(app.rechnungen.size, wieder.rechnungen.size)
        // jedes Dokument wird genau einmal aus einem im Export vorhandenen Pfad gelesen
        assertEquals(kopien.map { ExportPfade.dateiname(it.relPfad) }.toSet(), quellen.map { it.appName }.toSet())
        assertTrue(quellen.all { q -> kopien.any { it.relPfad == q.relPfad } })
    }

    @Test fun `SR-176f6f1f60df AC2 Dokumente Einreichungen und Erstattungen stimmen nach Round-Trip mit dem Export ueberein`() {
        val (export, _) = SicherungsPlaner.exportieren(beispiel())
        val (wieder, _) = SicherungsPlaner.wiederherstellen(SicherungJson.ausJson(SicherungJson.zuJson(export)))
        // erneuter Export des wiederhergestellten Bestands liefert exakt dieselbe Datei
        val (export2, kopien2) = SicherungsPlaner.exportieren(wieder)
        assertEquals(SicherungJson.zuJson(export), SicherungJson.zuJson(export2))
        assertEquals(export, export2)
        // alle Felder ausser den Pfaden entsprechen dem Original
        val orig = beispiel()
        assertEquals(orig.rechnungen.map { it.copy(pdfPfad = null) }, wieder.rechnungen.map { it.copy(pdfPfad = null) })
        assertEquals(orig.einreichungen.map { it.copy(dokumentPfad = null) }, wieder.einreichungen.map { it.copy(dokumentPfad = null) })
        assertEquals(orig.bescheide.map { it.copy(dokumentPfad = null) }, wieder.bescheide.map { it.copy(dokumentPfad = null) })
        assertEquals(orig.rechnungen.map { it.pdfPfad == null }, wieder.rechnungen.map { it.pdfPfad == null })
        assertTrue(kopien2.isNotEmpty())
        // Rechnung und ihre Einreichungen verweisen weiter auf dasselbe Dokument
        assertEquals(wieder.rechnungen[0].pdfPfad, wieder.einreichungen[0].dokumentPfad)
        assertEquals(wieder.rechnungen[0].pdfPfad, wieder.einreichungen[1].dokumentPfad)
    }

    @Test fun `SR-176f6f1f60df AC1 beschaedigte oder neuere Datendatei wird mit Fehler abgelehnt`() {
        for (text in listOf("kein json", "{}", "{\"formatVersion\": 99}", "{\"formatVersion\": 1}")) {
            try {
                SicherungJson.ausJson(text)
                fail("erwartet SicherungsFehler für $text")
            } catch (_: SicherungsFehler) {
            }
        }
    }

    @Test fun `SR-176f6f1f60df AC1 vorhandener Bestand wird erkannt damit vor dem Ersetzen nachgefragt wird`() {
        assertTrue(Sicherung(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap()).istLeer)
        assertFalse(beispiel().istLeer)
        val q = java.io.File("src/main/java/de/belegblitz/ui/settings/SettingsScreen.kt").readText()
        assertTrue(q.contains("vm.bestandVorhanden()") && q.contains("restore_confirm_title"))
    }
}

/** Issue #17: Hinweis in der Sicherung. */
class SicherungHinweisTest {
    private fun sicherung(rechnungHinweis: String?, bescheidHinweis: String?) = Sicherung(
        personen = listOf(Person(1, "Stefan")),
        rechnungen = listOf(Rechnung(1, 1, "Praxis", "Hausarzt", LocalDate.of(2026, 3, 5), 100, "T", null, null, 1, rechnungHinweis)),
        einreichungen = emptyList(),
        bescheide = listOf(Bescheid(1, Kasse.DAK, LocalDate.of(2026, 4, 1), null, null, bescheidHinweis)),
        positionen = emptyList(), beitraege = emptyList(), einstellungen = emptyMap(),
    )

    @Test fun `Issue 17 Hinweise werden exportiert und wiederhergestellt`() {
        val s = sicherung("Rückfrage \"ä\"\nZeile 2", "Bescheid prüfen")
        val zurueck = SicherungJson.ausJson(SicherungJson.zuJson(s))
        assertEquals("Rückfrage \"ä\"\nZeile 2", zurueck.rechnungen.single().hinweis)
        assertEquals("Bescheid prüfen", zurueck.bescheide.single().hinweis)
        assertEquals(s, zurueck)
    }

    @Test fun `Issue 17 fehlender Hinweis bleibt null`() {
        val zurueck = SicherungJson.ausJson(SicherungJson.zuJson(sicherung(null, null)))
        assertNull(zurueck.rechnungen.single().hinweis)
        assertNull(zurueck.bescheide.single().hinweis)
    }

    @Test fun `Issue 17 alte Sicherung ohne Hinweisfeld bleibt lesbar`() {
        val alt = org.json.JSONObject(SicherungJson.zuJson(sicherung("x", "y"))).also { o ->
            o.getJSONArray("rechnungen").getJSONObject(0).remove("hinweis")
            o.getJSONArray("bescheide").getJSONObject(0).remove("hinweis")
        }.toString()
        val gelesen = SicherungJson.ausJson(alt)
        assertNull(gelesen.rechnungen.single().hinweis)
        assertNull(gelesen.bescheide.single().hinweis)
        assertEquals("Praxis", gelesen.rechnungen.single().praxis)
    }
}
