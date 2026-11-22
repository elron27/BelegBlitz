package de.belegblitz.domain.update

import java.security.MessageDigest

/**
 * Neueste Version laut `latest.json` im Update-Ordner (App-Update, wie FotoSortierer U-1).
 * Die APK liegt im selben Ordner; [apkName] ist nur ein Dateiname, kein Pfad.
 */
data class RemoteRelease(
    val versionCode: Int,
    val versionName: String,
    val apkName: String,
    /** Erwartete SHA-256-Prüfsumme der APK (Hex, Kleinbuchstaben). */
    val sha256: String,
)

object UpdateRegeln {
    /** Nur neuere Versionen anbieten; ein Downgrade wird nie angeboten. */
    fun istNeuer(installierterVersionCode: Int, remote: RemoteRelease): Boolean =
        remote.versionCode > installierterVersionCode

    /** Nur einfache Dateinamen mit `.apk`, damit `latest.json` keine fremden Pfade angeben kann. */
    fun istSicherAPKName(name: String): Boolean =
        name.endsWith(".apk", ignoreCase = true) &&
            name.isNotBlank() &&
            !name.contains('/') &&
            !name.contains('\\') &&
            !name.startsWith(".")

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
