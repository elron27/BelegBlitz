package de.belegblitz.domain

import de.belegblitz.domain.matching.BescheidPlanung
import de.belegblitz.domain.matching.RechnungMerkmale
import de.belegblitz.domain.ocr.BescheidErgebnis
import de.belegblitz.domain.ocr.BescheidZeile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EingangTest {
    private val send = Eingang.ACTION_SEND
    private val view = Eingang.ACTION_VIEW

    // SR-4eb0053fe161, Kriterium 1: Teilen aus der Mail-App
    @Test fun `SR-4eb0053fe161 Kriterium 1 Teilen mit EXTRA_STREAM uebernimmt content-URI`() {
        val e = Eingang.auswerten(send, null, "content://mail/anhang/1", null, "application/pdf")
        assertEquals(EingangErgebnis.Uebernehmen("content://mail/anhang/1", EingangArt.PDF), e)
    }

    @Test fun `SR-4eb0053fe161 Kriterium 1 Teilen nur mit ClipData uebernimmt content-URI`() {
        val e = Eingang.auswerten(send, null, null, "content://mail/anhang/2", "application/pdf")
        assertEquals(EingangErgebnis.Uebernehmen("content://mail/anhang/2", EingangArt.PDF), e)
    }

    @Test fun `SR-4eb0053fe161 Kriterium 1 file-URI wird mit Fehler abgelehnt statt still ignoriert`() {
        val e = Eingang.auswerten(send, null, "file:///sdcard/a.pdf", null, "application/pdf")
        assertEquals(EingangErgebnis.Abgelehnt(EingangFehler.DATEI_URI, "file:///sdcard/a.pdf"), e)
        val keine = Eingang.auswerten(send, null, null, null, "application/pdf") as EingangErgebnis.Abgelehnt
        assertEquals(EingangFehler.KEINE_DATEI, keine.grund)
    }

    // SR-4eb0053fe161, Kriterium 2: Öffnen mit / Dateiauswahl liefert eine content-URI
    @Test fun `SR-4eb0053fe161 Kriterium 2 Oeffnen uebernimmt data-URI und fremde Aktionen werden ignoriert`() {
        assertEquals(
            EingangErgebnis.Uebernehmen("content://x/1", EingangArt.PDF),
            Eingang.auswerten(view, "content://x/1", null, null, "application/pdf"),
        )
        assertEquals(EingangErgebnis.Ignorieren, Eingang.auswerten("android.intent.action.MAIN", null, null, null, null))
        assertEquals(EingangErgebnis.Ignorieren, Eingang.auswerten(null, null, null, null, null))
    }

    // SR-4eb0053fe161, Kriterium 3: wie eine fotografierte Rechnung, hier: Seiten landen im selben Stapel
    @Test fun `SR-4eb0053fe161 Kriterium 3 importierte Seiten landen wie Fotos im Seitenstapel`() {
        val s = Seitenstapel<String>().hinzufuegen("foto1").hinzufuegen(listOf("pdf1", "pdf2"))
        assertEquals(listOf("foto1", "pdf1", "pdf2"), s.aktuell)
    }

    // SR-3273b2e9382d, Kriterium 1: Screenshot oder PDF per Teilen
    @Test fun `SR-3273b2e9382d Kriterium 1 Bescheid als Screenshot oder PDF per Teilen`() {
        val bild = Eingang.auswerten(send, null, "content://shots/1", null, "image/png") as EingangErgebnis.Uebernehmen
        assertEquals(EingangArt.BILD, bild.art)
        val pdf = Eingang.auswerten(send, null, "content://mail/b.pdf", null, "application/pdf") as EingangErgebnis.Uebernehmen
        assertEquals(EingangArt.PDF, pdf.art)
    }

    // SR-3273b2e9382d, Kriterium 2: Verknüpfung mit der Einreichung (Rechnung)
    @Test fun `SR-3273b2e9382d Kriterium 2 Bescheidposition wird der Rechnung zugeordnet und addiert`() {
        val datum = LocalDate.of(2026, 2, 3)
        val rechnungen = listOf(
            RechnungMerkmale(7, datum, "Zahnarzt", 12000),
            RechnungMerkmale(8, LocalDate.of(2026, 4, 1), "Hausarzt", 5000),
        )
        val ergebnis = BescheidErgebnis(
            kasse = null, schreibdatum = null, erstattungGesamtCent = null, hinweise = emptyList(), datenSeite = null,
            positionen = listOf(BescheidZeile(datum, 12000, 8000, "Zahnarzt")),
        )
        val plan = BescheidPlanung.planen(ergebnis, rechnungen)
        assertEquals(7L, plan.single().rechnungId)
        assertEquals(mapOf(7L to 8500L), BescheidPlanung.jeRechnung(listOf(7L to 8000L, 7L to 500L)))
        assertTrue(BescheidPlanung.jeRechnung(emptyList()).isEmpty())
    }
}
