package de.belegblitz.domain

import de.belegblitz.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SR-1571303342c4 (feste untere Navigation) und SR-70a2f4475fe0 (Bescheid-Ansicht).
 * Quelltext-Prüfungen für die UI-Anteile (Gradle-Arbeitsverzeichnis: app/).
 */
class HauptNavigationTest {
    private val ui = File("src/main/java/de/belegblitz/ui")
    private fun quelle(pfad: String) = File(ui, pfad).readText()

    @Test fun `SR-1571303342c4 Kriterium 1 vier Ziele in fester Reihenfolge mit passenden Routen`() {
        assertEquals(
            listOf(Reiter.RECHNUNGEN, Reiter.BESCHEIDE, Reiter.UEBERSICHT, Reiter.SUCHE), Reiter.entries,
        )
        assertEquals(
            listOf(Routes.LISTE, Routes.BESCHEIDE, Routes.UEBERSICHT, Routes.SUCHE), Reiter.entries.map { it.route },
        )
    }

    @Test fun `SR-1571303342c4 Kriterium 1 die vier Hauptansichten zeigen die Leiste mit ihrem Ziel`() {
        val erwartet = mapOf(
            "list/InvoiceListScreen.kt" to "Reiter.RECHNUNGEN", "BescheideScreen.kt" to "Reiter.BESCHEIDE",
            "auswertung/UebersichtScreen.kt" to "Reiter.UEBERSICHT", "auswertung/SucheScreen.kt" to "Reiter.SUCHE",
        )
        for ((datei, reiter) in erwartet) assertTrue(datei, quelle(datei).contains("ReiterLeiste($reiter, "))
    }

    @Test fun `SR-1571303342c4 Kriterium 2 Detail Erfassung Pruefen und Bescheid pruefen ohne Leiste`() {
        for (datei in listOf(
            "detail/InvoiceDetailScreen.kt", "detail/BescheidAnsichtScreen.kt", "erfassung/ErfassungScreen.kt",
            "erfassung/PruefenScreen.kt", "erfassung/BescheidPruefenScreen.kt",
        )) assertFalse(datei, quelle(datei).contains("ReiterLeiste("))
    }

    @Test fun `SR-1571303342c4 Kriterium 3 Wechsel zwischen Hauptzielen stapelt nicht`() {
        val nav = quelle("AppNavigation.kt")
        assertTrue(nav.contains("launchSingleTop = true; popUpTo(Routes.LISTE)"))
        assertTrue(nav.contains("nav.popBackStack(Routes.LISTE, false)"))
        assertEquals(Reiter.RECHNUNGEN.route, Routes.LISTE)
    }

    @Test fun `SR-1571303342c4 Kriterium 4 Ueberlaufmenue nur mit Einstellungen und kein Zurueck-Pfeil in Uebersicht und Suche`() {
        val menue = quelle("ReiterLeiste.kt").substringAfter("fun UeberlaufMenue")
        assertEquals(1, Regex("DropdownMenuItem[(]").findAll(menue).count())
        assertTrue(menue.contains("R.string.settings"))
        val liste = quelle("list/InvoiceListScreen.kt")
        assertFalse(liste.contains("R.string.search_title"))
        assertFalse(liste.contains("R.string.overview_title"))
        for (d in listOf("auswertung/UebersichtScreen.kt", "auswertung/SucheScreen.kt")) assertFalse(d, quelle(d).contains("ZurueckKnopf("))
    }

    @Test fun `SR-70a2f4475fe0 Kriterium 1 Klick im Reiter oeffnet die Bescheid-Ansicht mit Zurueck-Pfeil`() {
        assertEquals("bescheid/{id}", Routes.BESCHEID_ANSICHT)
        assertTrue(quelle("AppNavigation.kt").contains("nav.navigate(\"bescheid/\$it\")"))
        assertFalse(quelle("BescheideScreen.kt").contains("dokumentOeffnen"))
        assertTrue(quelle("detail/BescheidAnsichtScreen.kt").contains("ZurueckKnopf("))
    }

    @Test fun `SR-70a2f4475fe0 Kriterium 2 Positionen derselben Rechnung werden summiert`() {
        val z = BescheidAnsicht.rechnungsZeilen(listOf(10L to 500L, 11L to 300L, 10L to 250L))
        assertEquals(listOf(BescheidRechnungZeile(10, 750), BescheidRechnungZeile(11, 300)), z)
        assertEquals(1050L, BescheidAnsicht.summe(z))
        assertTrue(BescheidAnsicht.rechnungsZeilen(emptyList()).isEmpty())
    }

    @Test fun `SR-70a2f4475fe0 Kriterium 2 Klick auf Rechnung fuehrt zur Detailansicht`() {
        assertTrue(quelle("AppNavigation.kt").contains("onRechnung = { id -> nav.navigate(\"rechnung/\$id\") }"))
    }

    @Test fun `SR-70a2f4475fe0 Kriterium 3 Fehler wird geloggt und als Textzeile gezeigt`() {
        val q = quelle("detail/BescheidAnsichtScreen.kt")
        assertTrue(q.contains("PdfWerkzeug.pdfSeiten"))
        assertTrue(q.contains("Log.e("))
        assertTrue(q.contains("R.string.notice_pdf_error"))
        assertTrue(q.contains("R.string.notice_document_missing"))
    }

    @Test fun `SR-70a2f4475fe0 Kriterium 4 Extern oeffnen nur mit Dokument`() {
        val b = { pfad: String? -> BescheidKopf(1, Kasse.DAK, java.time.LocalDate.of(2026, 3, 1), pfad) }
        assertTrue(BescheidAnsicht.kannOeffnen(b("a.pdf")))
        assertFalse(BescheidAnsicht.kannOeffnen(b(null)))
        assertFalse(BescheidAnsicht.kannOeffnen(b(" ")))
        val q = quelle("detail/BescheidAnsichtScreen.kt")
        assertTrue(q.contains("R.string.notice_open_external") && q.contains("dokumentOeffnen("))
        for (d in listOf("values", "values-de")) {
            val x = File("src/main/res/$d/strings.xml").readText()
            assertTrue(d, x.contains("name=\"notice_open_external\""))
        }
    }
}
