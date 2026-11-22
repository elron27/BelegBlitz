package de.belegblitz.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import de.belegblitz.domain.Bildgroesse
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Android-seitige Bild- und PDF-Verarbeitung (SR-8d2f40b64013, SR-4eb0053fe161). Aufrufe nur auf Hintergrund-Threads. */
object PdfWerkzeug {
    private const val PDF_BREITE = 595 // A4 in Punkten

    /** Liest ein Foto, beachtet die EXIF-Drehung, verkleinert es und schreibt es als JPEG zurück. */
    fun fotoAufbereiten(datei: File) {
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(datei.path, grenzen)
        val faktor = Bildgroesse.faktor(grenzen.outWidth, grenzen.outHeight)
        var probe = 1
        while (grenzen.outWidth / (probe * 2) >= grenzen.outWidth * faktor) probe *= 2
        val roh = BitmapFactory.decodeFile(datei.path, BitmapFactory.Options().apply { inSampleSize = probe })
            ?: error("Foto nicht lesbar: ${datei.name}")
        val grad = Bildgroesse.exifGrad(
            ExifInterface(datei.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
        )
        val skala = Bildgroesse.faktor(roh.width, roh.height)
        val matrix = Matrix().apply { postScale(skala, skala); postRotate(grad.toFloat()) }
        val fertig = Bitmap.createBitmap(roh, 0, 0, roh.width, roh.height, matrix, true)
        FileOutputStream(datei).use { fertig.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (fertig !== roh) roh.recycle()
        fertig.recycle()
    }

    /** Je Bilddatei eine PDF-Seite; das Seitenverhältnis des Fotos bleibt erhalten. */
    fun bilderZuPdf(bilder: List<String>, ziel: File) {
        val pdf = PdfDocument()
        try {
            bilder.forEachIndexed { i, pfad ->
                val bmp = BitmapFactory.decodeFile(pfad) ?: error("Seite nicht lesbar: $pfad")
                val hoehe = Bildgroesse.pdfHoehe(PDF_BREITE, bmp.width, bmp.height)
                val seite = pdf.startPage(PdfDocument.PageInfo.Builder(PDF_BREITE, hoehe, i + 1).create())
                seite.canvas.apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bmp, null, RectF(0f, 0f, PDF_BREITE.toFloat(), hoehe.toFloat()), null)
                }
                pdf.finishPage(seite)
                bmp.recycle()
            }
            ziel.parentFile?.mkdirs()
            FileOutputStream(ziel).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    /** PDF-Seiten als JPEG-Dateien im Zielordner (importiertes PDF läuft so in denselben Ablauf wie Fotos). */
    fun pdfZuBildern(pdf: File, zielOrdner: File, breite: Int = 1400): List<String> {
        zielOrdner.mkdirs()
        val ergebnis = mutableListOf<String>()
        seiten(pdf, breite) { bmp ->
            val f = File(zielOrdner, "seite_${UUID.randomUUID()}.jpg")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            ergebnis += f.path
        }
        return ergebnis
    }

    /** Alle Seiten eines PDFs als Bitmaps (für die Detailansicht). */
    fun pdfSeiten(pdf: File, breite: Int): List<Bitmap> {
        val liste = mutableListOf<Bitmap>()
        seiten(pdf, breite) { liste += it.copy(Bitmap.Config.ARGB_8888, false) }
        return liste
    }

    private fun seiten(pdf: File, breite: Int, aktion: (Bitmap) -> Unit) {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { seite ->
                        val hoehe = Bildgroesse.pdfHoehe(breite, seite.width, seite.height)
                        val bmp = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        seite.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        aktion(bmp)
                        bmp.recycle()
                    }
                }
            }
        }
    }
}
