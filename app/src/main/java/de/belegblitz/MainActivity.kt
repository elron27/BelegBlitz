package de.belegblitz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

    /** SR-4eb0053fe161: PDF aus Teilen (ACTION_SEND) oder Öffnen (ACTION_VIEW) an die Navigation weiterreichen. */
    private fun uebernehme(intent: Intent?, container: AppContainer) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) container.eingehendesPdf.value = uri
    }
}
