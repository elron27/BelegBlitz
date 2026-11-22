package de.belegblitz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import de.belegblitz.domain.Eingang
import de.belegblitz.domain.EingangErgebnis
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.belegblitz.ui.AppNavigation
import de.belegblitz.ui.theme.BelegBlitzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BelegBlitzApp).container
        if (savedInstanceState == null) uebernehme(intent, container)
        setContent {
            BelegBlitzTheme { AppNavigation(container) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        uebernehme(intent, (application as BelegBlitzApp).container)
    }

    /** SR-4eb0053fe161: PDF aus Teilen (ACTION_SEND, EXTRA_STREAM oder ClipData) oder Öffnen (ACTION_VIEW) übernehmen. */
    private fun uebernehme(intent: Intent?, container: AppContainer) {
        if (intent == null) return
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val clip = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        when (val e = Eingang.auswerten(intent.action, intent.data?.toString(), stream?.toString(), clip?.toString(), intent.type)) {
            is EingangErgebnis.Uebernehmen -> container.eingehendesPdf.value = Uri.parse(e.uri)
            is EingangErgebnis.Abgelehnt -> {
                Log.e("Eingang", "Datei nicht übernommen: ${e.grund} ${e.uri}")
                Toast.makeText(this, R.string.error_import_uri, Toast.LENGTH_LONG).show()
            }
            EingangErgebnis.Ignorieren -> Unit
        }
    }
}
