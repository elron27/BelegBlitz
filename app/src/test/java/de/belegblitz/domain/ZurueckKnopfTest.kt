package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** SR-daf053d403cc (SR-d403cc): Quelltext-Prüfungen für den gemeinsamen Zurück-Knopf (Gradle-Arbeitsverzeichnis: app/). */
/**
 * Übersicht und Suche sind seit SR-1571303342c4 Hauptziele mit unterer Leiste und haben keinen Zurück-Pfeil mehr;
 * sie sind deshalb nicht mehr in der Liste. Die Bescheid-Ansicht (SR-70a2f4475fe0) ist neu dabei.
 */
class ZurueckKnopfTest {
    private val ui = File("src/main/java/de/belegblitz/ui")
    private val screens = listOf(
        "detail/InvoiceDetailScreen.kt", "detail/BescheidAnsichtScreen.kt",
        "settings/SettingsScreen.kt", "erfassung/PruefenScreen.kt", "erfassung/BescheidPruefenScreen.kt",
    )

    @Test fun `SR-daf053d403cc Kriterium 1 Knopf ist IconButton mit mindestens 48 dp`() {
        val q = File(ui, "ZurueckKnopf.kt").readText()
        assertTrue(q.contains("IconButton("))
        assertTrue(q.contains("TitelleistenKnopfGroesse = 48.dp"))
    }

    @Test fun `SR-daf053d403cc Kriterium 2 Beschreibungstext in Deutsch und Englisch`() {
        assertTrue(File("src/main/java/de/belegblitz/ui/ZurueckKnopf.kt").readText().contains("R.string.back"))
        for (d in listOf("values", "values-de")) {
            assertTrue(d, Regex("<string name=\"back\">[^<]+</string>").containsMatchIn(File("src/main/res/$d/strings.xml").readText()))
        }
    }

    @Test fun `SR-daf053d403cc Kriterium 3 alle Bildschirme nutzen den gemeinsamen Knopf`() {
        for (s in screens) {
            val q = File(ui, s).readText()
            assertTrue(s, q.contains("ZurueckKnopf("))
            assertFalse(s, q.contains("Text(stringResource(R.string.back))"))
        }
        assertEquals(5, screens.size)
    }
}
