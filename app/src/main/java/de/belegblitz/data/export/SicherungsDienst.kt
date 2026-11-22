package de.belegblitz.data.export

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import de.belegblitz.data.AppDatabase
import de.belegblitz.data.Einstellungen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Ergebnis eines Laufs; fehlende Dokumente sind Warnungen, kein Abbruch. */
data class SicherungsErgebnis(val rechnungen: Int, val bescheide: Int, val dokumente: Int, val fehlendeDokumente: Int)

/**
 * SR-e334c167571f (Export) und SR-176f6f1f60df (Wiederherstellung): schreibt/liest den vom Nutzer per Systemauswahl
 * gewählten Ordner. Aufruf aus Coroutinen; die Arbeit läuft auf Dispatchers.IO.
 */
class SicherungsDienst(
    private val context: Context,
    private val db: AppDatabase,
    private val einstellungen: Einstellungen,
    private val pdfOrdner: () -> File,
) {
    private val tag = "Sicherung"

    suspend fun bestandVorhanden(): Boolean = withContext(Dispatchers.IO) { !lesen().istLeer }

    private suspend fun lesen(): Sicherung {
        val d = db.sicherungDao()
        return Sicherung(
            d.personen(), d.rechnungen(), d.einreichungen(), d.bescheide(), d.positionen(), d.beitraege(),
            mapOf(
                Sicherung.EINST_WARTEZEIT to einstellungen.wartezeitWochen.value.toString(),
                Sicherung.EINST_SPERRE to einstellungen.sperreAktiv.value.toString(),
            ),
        )
    }

    /** @param fortschritt (erledigt, gesamt) */
    suspend fun exportieren(zielUri: Uri, fortschritt: (Int, Int) -> Unit): SicherungsErgebnis = withContext(Dispatchers.IO) {
        try {
            val wurzel = DocumentFile.fromTreeUri(context, zielUri)?.takeIf { it.isDirectory && it.canWrite() }
                ?: throw SicherungsFehler("Zielordner nicht beschreibbar")
            val app = lesen()
            val (export, kopien) = SicherungsPlaner.exportieren(app)
            val gesamt = kopien.size + 1
            var fehlend = 0
            kopien.forEachIndexed { i, k ->
                fortschritt(i, gesamt)
                val quelle = File(pdfOrdner(), k.appName)
                if (!quelle.isFile) {
                    Log.w(tag, "Dokument fehlt im App-Speicher: ${k.appName}")
                    fehlend++
                    return@forEachIndexed
                }
                val ziel = datei(wurzel, k.relPfad.split('/'))
                context.contentResolver.openOutputStream(ziel.uri, "wt")!!.use { out -> quelle.inputStream().use { it.copyTo(out) } }
            }
            val daten = datei(wurzel, listOf(ExportPfade.DATENDATEI))
            context.contentResolver.openOutputStream(daten.uri, "wt")!!.use { it.write(SicherungJson.zuJson(export).toByteArray(Charsets.UTF_8)) }
            fortschritt(gesamt, gesamt)
            SicherungsErgebnis(app.rechnungen.size, app.bescheide.size, kopien.size - fehlend, fehlend)
        } catch (e: Exception) {
            Log.e(tag, "Export fehlgeschlagen", e)
            throw e
        }
    }

    /** Ersetzt den gesamten Bestand durch den Inhalt des Ordners. Vorher beim Nutzer nachfragen, falls [bestandVorhanden]. */
    suspend fun wiederherstellen(quelleUri: Uri, fortschritt: (Int, Int) -> Unit): SicherungsErgebnis = withContext(Dispatchers.IO) {
        try {
            val wurzel = DocumentFile.fromTreeUri(context, quelleUri)?.takeIf { it.isDirectory }
                ?: throw SicherungsFehler("Quellordner nicht lesbar")
            val dd = wurzel.findFile(ExportPfade.DATENDATEI) ?: throw SicherungsFehler("Keine ${ExportPfade.DATENDATEI} im Ordner")
            val text = context.contentResolver.openInputStream(dd.uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
            val (app, kopien) = SicherungsPlaner.wiederherstellen(SicherungJson.ausJson(text))

            val gesamt = kopien.size + 1
            val ordner = pdfOrdner()
            // Dokumente zuerst in einen Zwischenordner, damit ein Fehler den alten Bestand nicht beschädigt
            val neu = File(ordner.parentFile, "rechnungen_neu").apply { deleteRecursively(); mkdirs() }
            var fehlend = 0
            kopien.forEachIndexed { i, k ->
                fortschritt(i, gesamt)
                val quelle = suche(wurzel, k.relPfad.split('/'))
                if (quelle == null) {
                    Log.w(tag, "Dokument fehlt im Export: ${k.relPfad}")
                    fehlend++
                    return@forEachIndexed
                }
                context.contentResolver.openInputStream(quelle.uri)!!.use { i2 -> File(neu, k.appName).outputStream().use { i2.copyTo(it) } }
            }
            db.withTransaction {
                val d = db.sicherungDao()
                d.loeschePositionen(); d.loescheEinreichungen(); d.loescheBescheide()
                d.loescheRechnungen(); d.loescheBeitraege(); d.loeschePersonen()
                d.einfuegenPersonen(app.personen); d.einfuegenRechnungen(app.rechnungen)
                d.einfuegenEinreichungen(app.einreichungen); d.einfuegenBescheide(app.bescheide)
                d.einfuegenPositionen(app.positionen); d.einfuegenBeitraege(app.beitraege)
            }
            ordner.deleteRecursively()
            if (!neu.renameTo(ordner)) throw SicherungsFehler("Dokumentordner konnte nicht ersetzt werden")
            app.einstellungen[Sicherung.EINST_WARTEZEIT]?.toIntOrNull()?.let { einstellungen.setWartezeitWochen(it) }
            fortschritt(gesamt, gesamt)
            SicherungsErgebnis(app.rechnungen.size, app.bescheide.size, kopien.size - fehlend, fehlend)
        } catch (e: Exception) {
            Log.e(tag, "Wiederherstellung fehlgeschlagen", e)
            throw e
        }
    }

    /** Legt die Ordner entlang des Pfads an und gibt die (ggf. neue) Zieldatei zurück; die letzte Komponente ist die Datei. */
    private fun datei(wurzel: DocumentFile, teile: List<String>): DocumentFile {
        var dir = wurzel
        for (t in teile.dropLast(1)) {
            dir = dir.findFile(t)?.takeIf { it.isDirectory } ?: dir.createDirectory(t) ?: throw SicherungsFehler("Ordner nicht anlegbar: $t")
        }
        val name = teile.last()
        return dir.findFile(name) ?: dir.createFile(if (name.endsWith(".pdf")) "application/pdf" else "application/json", name)?.let { f ->
            // Manche Provider hängen eine Endung an; der Name muss exakt stimmen
            if (f.name != name) f.renameTo(name)
            f
        } ?: throw SicherungsFehler("Datei nicht anlegbar: $name")
    }

    private fun suche(wurzel: DocumentFile, teile: List<String>): DocumentFile? =
        teile.fold(wurzel as DocumentFile?) { d, t -> d?.findFile(t) }
}
