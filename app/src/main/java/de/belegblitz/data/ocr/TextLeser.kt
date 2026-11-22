package de.belegblitz.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.belegblitz.domain.RechnungErkenner
import de.belegblitz.domain.RechnungVorschlag
import de.belegblitz.domain.ocr.RechnungParser
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** SR-a7d7afd41c64: liest den Text eines Seitenbilds (Schnittstelle, damit die Auswertung ohne ML Kit testbar bleibt). */
interface TextLeser {
    /** @return Text mit einer Zeile je Zeile des Bildes; wirft bei Fehlern. */
    suspend fun lesen(bildPfad: String): String
}

/**
 * SR-a7d7afd41c64 (Kriterium 2): ML Kit Text Recognition, lateinisches Modell, im APK gebündelt und vollständig
 * auf dem Gerät; die App besitzt keine Netzwerkberechtigung.
 */
class MlKitTextLeser : TextLeser {
    private val erkenner by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun lesen(bildPfad: String): String {
        val bild = laden(bildPfad)
        try {
            return suspendCancellableCoroutine { weiter ->
                erkenner.process(InputImage.fromBitmap(bild, 0))
                    .addOnSuccessListener { weiter.resume(it.text) }
                    .addOnFailureListener { weiter.resumeWithException(it) }
            }
        } finally {
            bild.recycle()
        }
    }

    /** Lädt das Bild höchstens mit [MAX_SEITE] Pixeln Kantenlänge, damit große Fotos den Speicher nicht sprengen. */
    private fun laden(pfad: String): Bitmap {
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(pfad, grenzen)
        var teiler = 1
        while (maxOf(grenzen.outWidth, grenzen.outHeight) / teiler > MAX_SEITE) teiler *= 2
        return BitmapFactory.decodeFile(pfad, BitmapFactory.Options().apply { inSampleSize = teiler })
            ?: error("Bild nicht lesbar: $pfad")
    }

    companion object { private const val MAX_SEITE = 3200 }
}

/** Liest alle Seiten nacheinander; eine fehlgeschlagene Seite wird geloggt und übersprungen, nicht verschluckt. */
suspend fun TextLeser.seitenLesen(pfade: List<String>, tag: String = "Ocr"): List<String> {
    val texte = mutableListOf<String>()
    var letzterFehler: Exception? = null
    for (p in pfade) {
        try {
            texte += lesen(p)
        } catch (e: Exception) {
            Log.e(tag, "Texterkennung fehlgeschlagen für $p", e)
            letzterFehler = e
            texte += ""
        }
    }
    if (pfade.isNotEmpty() && texte.all { it.isEmpty() } && letzterFehler != null) throw letzterFehler
    return texte
}

/** SR-a7d7afd41c64, SR-84e9478a8eb5: Erkenner für den Erfassungsablauf; liefert Vorschlag und OCR-Text zum Speichern. */
class OcrRechnungErkenner(private val leser: TextLeser) : RechnungErkenner {
    override suspend fun erkennen(seitenPfade: List<String>): RechnungVorschlag {
        val seiten = leser.seitenLesen(seitenPfade, "RechnungErkenner")
        val text = seiten.joinToString("\n\n").trim()
        return RechnungParser.auswerten(seiten).alsVorschlag(text.ifEmpty { null })
    }
}
