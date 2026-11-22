package de.belegblitz.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import de.belegblitz.BuildConfig
import de.belegblitz.R
import de.belegblitz.domain.update.RemoteRelease
import de.belegblitz.domain.update.UpdateRegeln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Prüft einen Update-Ordner und installiert eine neuere Version (App-Update).
 *
 * Der Ordner wird über die Ordnerauswahl des Systems (SAF) gewählt. Das kann ein vom NAS kopierter Ordner auf
 * dem Handy sein oder, wenn der Dateimanager das NAS als Speicherort anbietet, der NAS-Ordner selbst. Darin liegt
 * eine `latest.json` mit Version, APK-Name und SHA-256 neben der APK. Die Installation bestätigt Android selbst;
 * die APK muss mit demselben Schlüssel signiert sein, sonst lehnt Android sie ab. Daten und Einstellungen
 * bleiben bei einem Update erhalten. Die App braucht dafür keine Netzwerk-Berechtigung.
 */
class AppUpdater(private val context: Context) {

    sealed interface CheckResult {
        data object UpToDate : CheckResult
        data class Available(val release: RemoteRelease, val folder: Uri) : CheckResult
        data class Failed(@StringRes val message: Int, val detail: String? = null) : CheckResult
    }

    /** Liest `latest.json` aus dem gewählten Ordner (oder dessen Unterordner `final`) und vergleicht die Version. */
    suspend fun check(folder: Uri?): CheckResult = withContext(Dispatchers.IO) {
        if (folder == null) return@withContext CheckResult.Failed(R.string.update_error_no_folder)
        val release = try {
            val location = locate(folder)
            parseManifest(read(folder, location.children.getValue(MANIFEST_NAME).docId))
        } catch (e: MissingManifestException) {
            return@withContext CheckResult.Failed(R.string.update_error_not_found)
        } catch (e: IOException) {
            return@withContext CheckResult.Failed(R.string.update_error_read, e.message)
        } catch (e: SecurityException) {
            return@withContext CheckResult.Failed(R.string.update_error_read, e.message)
        } catch (e: JSONException) {
            return@withContext CheckResult.Failed(R.string.update_error_manifest)
        }
        if (UpdateRegeln.istNeuer(BuildConfig.VERSION_CODE, release)) {
            CheckResult.Available(release, folder)
        } else {
            CheckResult.UpToDate
        }
    }

    /** Kopiert die APK in den Cache, prüft die SHA-256-Prüfsumme und gibt die Datei zurück oder einen Fehler. */
    suspend fun download(release: RemoteRelease, folder: Uri): Result<File> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { deleteRecursively(); mkdirs() }
        val file = File(dir, release.apkName)
        try {
            val apk = locate(folder).children[release.apkName] ?: throw IOException(release.apkName)
            val digest = MessageDigest.getInstance("SHA-256")
            open(folder, apk.docId).use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (UpdateRegeln.hex(digest.digest()) != release.sha256.lowercase()) {
                file.delete()
                return@withContext Result.failure(UpdateException(R.string.update_error_hash))
            }
            Result.success(file)
        } catch (e: MissingManifestException) {
            Result.failure(UpdateException(R.string.update_error_not_found))
        } catch (e: IOException) {
            Result.failure(UpdateException(R.string.update_error_read, e.message))
        } catch (e: SecurityException) {
            Result.failure(UpdateException(R.string.update_error_read, e.message))
        }
    }

    /**
     * Öffnet den Android-Installationsdialog. Ohne die Erlaubnis „Apps aus dieser Quelle installieren“
     * wird stattdessen deren Einstellungsseite geöffnet.
     */
    @StringRes
    fun install(file: File): Int? {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return R.string.update_error_permission
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return null
    }

    private class Node(val docId: String, val isDir: Boolean)

    private class Location(val children: Map<String, Node>)

    private class MissingManifestException : Exception()

    /** Findet den Ordner mit `latest.json`: den gewählten oder, wenn dort keine liegt, dessen Unterordner `final`. */
    private fun locate(tree: Uri): Location {
        val root = children(tree, DocumentsContract.getTreeDocumentId(tree))
        if (MANIFEST_NAME in root) return Location(root)
        val final = root[FINAL_DIR]
        if (final != null && final.isDir) {
            val inFinal = children(tree, final.docId)
            if (MANIFEST_NAME in inFinal) return Location(inFinal)
        }
        throw MissingManifestException()
    }

    private fun children(tree: Uri, parentDocId: String): Map<String, Node> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val result = HashMap<String, Node>()
        val cursor = context.contentResolver.query(uri, projection, null, null, null) ?: throw IOException(uri.toString())
        cursor.use {
            while (it.moveToNext()) {
                result[it.getString(1)] = Node(it.getString(0), it.getString(2) == Document.MIME_TYPE_DIR)
            }
        }
        return result
    }

    private fun open(tree: Uri, docId: String) =
        context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, docId))
            ?: throw IOException(docId)

    private fun read(tree: Uri, docId: String): ByteArray = open(tree, docId).use { it.readBytes() }

    private fun parseManifest(bytes: ByteArray): RemoteRelease {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val apkName = json.getString("apk")
        if (!UpdateRegeln.istSicherAPKName(apkName)) throw JSONException("Ungültiger APK-Name: $apkName")
        return RemoteRelease(
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName"),
            apkName = apkName,
            sha256 = json.getString("sha256"),
        )
    }

    /** Fehler mit Meldung für die Anzeige. */
    class UpdateException(@StringRes val messageRes: Int, val detail: String? = null) : Exception(detail)

    companion object {
        const val MANIFEST_NAME = "latest.json"
        private const val FINAL_DIR = "final"
        private const val UPDATE_DIR = "updates"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val BUFFER_SIZE = 64 * 1024
    }
}
