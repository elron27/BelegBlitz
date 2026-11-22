package de.belegblitz.data.export

import de.belegblitz.data.Bescheid
import de.belegblitz.data.BescheidPosition
import de.belegblitz.data.Einreichung
import de.belegblitz.data.Person
import de.belegblitz.data.Rechnung
import de.belegblitz.data.VkbBeitrag

/**
 * SR-e334c167571f, SR-176f6f1f60df: vollständiger Datenbestand der App.
 * Im Export tragen die Dokumentpfade (pdfPfad/dokumentPfad) relative Pfade `Kasse/Jahr/Dokumente/Datei.pdf`,
 * im App-Bestand den Dateinamen im app-internen Ordner.
 */
data class Sicherung(
    val personen: List<Person>,
    val rechnungen: List<Rechnung>,
    val einreichungen: List<Einreichung>,
    val bescheide: List<Bescheid>,
    val positionen: List<BescheidPosition>,
    val beitraege: List<VkbBeitrag>,
    val einstellungen: Map<String, String>,
) {
    val istLeer: Boolean get() = rechnungen.isEmpty() && bescheide.isEmpty() && beitraege.isEmpty()

    companion object {
        const val FORMAT_VERSION = 1
        const val EINST_WARTEZEIT = "wartezeitWochen"
        const val EINST_SPERRE = "sperreAktiv"
    }
}

/** Ein zu kopierendes Dokument: Dateiname im App-Ordner und relativer Pfad im Export. */
data class DokumentKopie(val appName: String, val relPfad: String)

class SicherungsFehler(message: String, cause: Throwable? = null) : Exception(message, cause)

/** SR-e334c167571f: Ordner- und Dateinamen des Exports (reine Logik). */
object ExportPfade {
    const val DATENDATEI = "belegblitz-daten.json"
    const val DOKUMENTE = "Dokumente"
    const val OHNE_KASSE = "Ohne_Kasse"

    fun sauber(name: String): String {
        val s = name.map { if (it in "\\/:*?\"<>|" || it.isISOControl()) '_' else it }.joinToString("").trim().trim('.')
        return s.ifEmpty { "Dokument" }
    }

    fun ordner(kasse: String, jahr: Int) = "$kasse/$jahr"
    fun relPfad(kasseJahr: String, dateiname: String) = "$kasseJahr/$DOKUMENTE/$dateiname"
    fun dateiname(relPfad: String) = relPfad.substringAfterLast('/')

    /** Hängt bei Namensgleichheit (ohne Beachtung der Groß-/Kleinschreibung) _2, _3 ... vor die Endung. */
    fun eindeutig(wunsch: String, belegt: Set<String>): String {
        val belegtKlein = belegt.map { it.lowercase() }.toSet()
        if (wunsch.lowercase() !in belegtKlein) return wunsch
        val basis = wunsch.substringBeforeLast('.', wunsch)
        val ende = if (wunsch.contains('.')) "." + wunsch.substringAfterLast('.') else ""
        var n = 2
        while ("${basis}_$n$ende".lowercase() in belegtKlein) n++
        return "${basis}_$n$ende"
    }
}

/** SR-e334c167571f, SR-176f6f1f60df: rechnet zwischen App-Bestand und Export-Struktur um. */
object SicherungsPlaner {
    private class Dok(val name: String, val ordner: LinkedHashSet<String> = linkedSetOf())

    /** App-Bestand (Dateinamen) -> Export-Daten (relative Pfade) und Liste der zu kopierenden Dokumente. */
    fun exportieren(app: Sicherung): Pair<Sicherung, List<DokumentKopie>> {
        val docs = linkedMapOf<String, Dok>()
        val belegt = mutableSetOf<String>()
        fun dok(appName: String, wunsch: String, ordner: String): Dok {
            val d = docs.getOrPut(appName) {
                Dok(ExportPfade.eindeutig(ExportPfade.sauber(wunsch) , belegt).also { belegt += it })
            }
            d.ordner += ordner
            return d
        }
        val rechnungen = app.rechnungen.associateBy { it.id }
        val kassenJeRechnung = app.einreichungen.groupBy { it.rechnungId }.mapValues { (_, l) -> l.map { it.kasse }.distinct().sorted() }

        for (r in app.rechnungen) {
            val p = r.pdfPfad ?: continue
            val kassen = kassenJeRechnung[r.id].orEmpty().map { it.name }.ifEmpty { listOf(ExportPfade.OHNE_KASSE) }
            kassen.forEach { dok(p, r.titel + ".pdf", ExportPfade.ordner(it, r.rechnungsdatum.year)) }
        }
        for (e in app.einreichungen) {
            val p = e.dokumentPfad ?: continue
            val r = rechnungen[e.rechnungId] ?: continue
            dok(p, p, ExportPfade.ordner(e.kasse.name, r.rechnungsdatum.year))
        }
        for (b in app.bescheide) {
            val p = b.dokumentPfad ?: continue
            dok(p, "Bescheid_${b.kasse.name}_${b.datum}.pdf", ExportPfade.ordner(b.kasse.name, b.datum.year))
        }

        fun rel(appName: String?, bevorzugt: String?): String? {
            val d = docs[appName ?: return null] ?: return null
            val o = if (bevorzugt != null && bevorzugt in d.ordner) bevorzugt else d.ordner.first()
            return ExportPfade.relPfad(o, d.name)
        }
        val rechnungenNeu = app.rechnungen.map { r -> r.copy(pdfPfad = rel(r.pdfPfad, null)) }
        val einreichungenNeu = app.einreichungen.map { e ->
            val jahr = rechnungen[e.rechnungId]?.rechnungsdatum?.year
            e.copy(dokumentPfad = rel(e.dokumentPfad, jahr?.let { ExportPfade.ordner(e.kasse.name, it) }))
        }
        val bescheideNeu = app.bescheide.map { b -> b.copy(dokumentPfad = rel(b.dokumentPfad, ExportPfade.ordner(b.kasse.name, b.datum.year))) }
        val kopien = docs.map { (appName, d) -> d.ordner.map { DokumentKopie(appName, ExportPfade.relPfad(it, d.name)) } }.flatten()
        return app.copy(rechnungen = rechnungenNeu, einreichungen = einreichungenNeu, bescheide = bescheideNeu) to kopien
    }

    /**
     * Export-Daten (relative Pfade) -> App-Bestand (Dateinamen). Liefert je Dokument genau einen Quellpfad;
     * gleiche Dateinamen in mehreren Kassenordnern sind dasselbe Dokument.
     */
    fun wiederherstellen(export: Sicherung): Pair<Sicherung, List<DokumentKopie>> {
        val quellen = linkedMapOf<String, String>() // Dateiname -> erster relativer Pfad
        fun name(rel: String?): String? = rel?.let {
            val n = ExportPfade.dateiname(it)
            quellen.putIfAbsent(n, it)
            n
        }
        val neu = export.copy(
            rechnungen = export.rechnungen.map { it.copy(pdfPfad = name(it.pdfPfad)) },
            einreichungen = export.einreichungen.map { it.copy(dokumentPfad = name(it.dokumentPfad)) },
            bescheide = export.bescheide.map { it.copy(dokumentPfad = name(it.dokumentPfad)) },
        )
        return neu to quellen.map { (n, rel) -> DokumentKopie(n, rel) }
    }
}
