package de.belegblitz.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Prüft Manifest, Abhängigkeiten und Quellen (Unit-Tests laufen im Modulordner `app`). */
class LokalUndOhneSchnittstelleTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
    private val gradle = File("build.gradle.kts").readText(Charsets.UTF_8)
    private val quellen = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
        .map { it.name to it.readText(Charsets.UTF_8) }.toList()

    // SR-e2b34f266d24, Kriterium 1: keine INTERNET-Berechtigung
    @Test fun `SR-e2b34f266d24 Kriterium 1 keine INTERNET-Berechtigung`() {
        val zeilen = manifest.lines().filter { it.contains("android.permission.INTERNET") }
        assertTrue(
            "INTERNET muss per tools:node=remove entfernt sein",
            zeilen.isNotEmpty() && zeilen.all { it.contains("tools:node=\"remove\"") },
        )
    }

    // SR-e2b34f266d24, Kriterium 2: Daten im Gerätespeicher
    @Test fun `SR-e2b34f266d24 Kriterium 2 Daten und Dokumente liegen im app-internen Speicher`() {
        val container = quellen.first { it.first == "AppContainer.kt" }.second
        assertTrue(container.contains("context.filesDir"))
        quellen.forEach { (name, text) ->
            assertFalse("$name nutzt externen Speicher", text.contains("getExternalStorage") || text.contains("Environment.DIRECTORY"))
        }
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    // SR-11e6ec34307f, Kriterium 1: keine Schnittstelle zu DAK/VKB
    @Test fun `SR-11e6ec34307f Kriterium 1 keine Kassen-Schnittstelle oder HTTP-Bibliothek`() {
        val verboten = listOf("okhttp", "retrofit", "ktor", "volley", "apache.http", "cronet")
        verboten.forEach { assertFalse("Abhängigkeit $it", gradle.lowercase().contains(it)) }
        val api = listOf("HttpURLConnection", "HttpsURLConnection", "java.net.URL(", "java.net.Socket", "WebView", "okhttp3", "retrofit2")
        quellen.forEach { (name, text) -> api.forEach { assertFalse("$name enthält $it", text.contains(it)) } }
    }

    // SR-11e6ec34307f, Kriterium 2: Hochladen macht der Nutzer in den Kassen-Apps, die App verweist nicht auf sie
    @Test fun `SR-11e6ec34307f Kriterium 2 keine Verweise auf Kassen-Apps oder Dienste`() {
        val kassen = listOf("com.dak", "de.dak", "vkb.de", "dak.de", "meine-vkb")
        quellen.forEach { (name, text) -> kassen.forEach { assertFalse("$name verweist auf $it", text.lowercase().contains(it)) } }
        assertFalse(manifest.contains("<queries"))
    }
}
