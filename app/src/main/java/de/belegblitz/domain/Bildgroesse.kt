package de.belegblitz.domain

/** SR-8d2f40b64013: Fotos vor dem PDF auf eine sinnvolle Größe verkleinern (lesbar, aber kein Riesen-PDF). */
object Bildgroesse {
    const val MAX_KANTE = 1800

    /** Faktor (höchstens 1) für die längere Kante; vergrößert nie. */
    fun faktor(breite: Int, hoehe: Int, maxKante: Int = MAX_KANTE): Float {
        val laenge = maxOf(breite, hoehe)
        return if (laenge <= maxKante || laenge <= 0) 1f else maxKante.toFloat() / laenge
    }

    /** Drehung in Grad aus dem EXIF-Wert (ExifInterface.ORIENTATION_*: 3 = 180, 6 = 90, 8 = 270). */
    fun exifGrad(orientation: Int): Int = when (orientation) { 3 -> 180; 6 -> 90; 8 -> 270; else -> 0 }

    /** Seitenhöhe in PDF-Punkten bei fester Breite, passend zum Seitenverhältnis des Fotos. */
    fun pdfHoehe(pdfBreite: Int, bildBreite: Int, bildHoehe: Int): Int =
        if (bildBreite <= 0) pdfBreite else maxOf(1, Math.round(pdfBreite.toFloat() * bildHoehe / bildBreite))
}
