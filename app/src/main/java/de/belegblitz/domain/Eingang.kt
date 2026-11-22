package de.belegblitz.domain

/** Art einer von außen übergebenen Datei. */
enum class EingangArt { PDF, BILD }

enum class EingangFehler { KEINE_DATEI, DATEI_URI }

sealed interface EingangErgebnis {
    data class Uebernehmen(val uri: String, val art: EingangArt) : EingangErgebnis
    data class Abgelehnt(val grund: EingangFehler, val uri: String?) : EingangErgebnis
    /** Kein Teilen/Öffnen-Intent. */
    data object Ignorieren : EingangErgebnis
}

/**
 * SR-4eb0053fe161, SR-3273b2e9382d: Auswertung der Intents zum Teilen (ACTION_SEND) und Öffnen (ACTION_VIEW)
 * ohne Android-Abhängigkeit. Übernommen werden nur content://-Uris; file://-Uris sind ohne Berechtigung
 * nicht lesbar und werden mit Grund abgelehnt, damit die App eine Meldung zeigen kann.
 */
object Eingang {
    fun auswerten(action: String?, data: String?, streamUri: String?, clipUri: String?, mime: String?): EingangErgebnis {
        val uri = when (action) {
            ACTION_SEND -> streamUri ?: clipUri
            ACTION_VIEW -> data
            else -> return EingangErgebnis.Ignorieren
        }
        if (uri.isNullOrBlank()) return EingangErgebnis.Abgelehnt(EingangFehler.KEINE_DATEI, null)
        if (!uri.startsWith("content://", ignoreCase = true)) return EingangErgebnis.Abgelehnt(EingangFehler.DATEI_URI, uri)
        val art = if (mime?.startsWith("image/", ignoreCase = true) == true) EingangArt.BILD else EingangArt.PDF
        return EingangErgebnis.Uebernehmen(uri, art)
    }

    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_VIEW = "android.intent.action.VIEW"
}
