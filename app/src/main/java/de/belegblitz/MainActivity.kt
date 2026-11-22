package de.belegblitz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.belegblitz.domain.Eingang
import de.belegblitz.domain.EingangErgebnis
import de.belegblitz.domain.Sperre
import de.belegblitz.ui.AppNavigation
import de.belegblitz.ui.theme.BelegBlitzTheme

/** FragmentActivity statt ComponentActivity, weil BiometricPrompt sie braucht (SR-c43e8d9ab0a8). */
class MainActivity : FragmentActivity() {
    private var hintergrundSeit: Long? = null
    private var abfrageLaeuft = false

    private val benachrichtigungen =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BelegBlitzApp).container
        if (savedInstanceState == null) uebernehme(intent, container)
        // SR-aada699191a1: ab Android 13 Erlaubnis für die Erinnerung erfragen
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) benachrichtigungen.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            BelegBlitzTheme {
                val gesperrt by container.gesperrt.collectAsStateWithLifecycle()
                if (gesperrt) SperrBildschirm(onEntsperren = { entsperren(container) }) else AppNavigation(container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val container = (application as BelegBlitzApp).container
        // SR-c43e8d9ab0a8: nach kurzer Frist im Hintergrund erneut sperren
        if (Sperre.beimZurueckkehrenGesperrt(
                container.einstellungen.sperreAktiv.value, hintergrundSeit, SystemClock.elapsedRealtime(),
            )
        ) container.gesperrt.value = true
        hintergrundSeit = null
        if (container.gesperrt.value) entsperren(container)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) hintergrundSeit = SystemClock.elapsedRealtime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        uebernehme(intent, (application as BelegBlitzApp).container)
    }

    /** Fingerabdruck, ersatzweise Gerätesperre. Ohne eingerichtete Sperre wird nicht ausgesperrt. */
    private fun entsperren(container: AppContainer) {
        if (abfrageLaeuft) return
        val art = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(art) != BiometricManager.BIOMETRIC_SUCCESS) {
            Log.w("Sperre", "Keine Authentifizierung verfügbar, App bleibt offen")
            container.gesperrt.value = false
            return
        }
        abfrageLaeuft = true
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    abfrageLaeuft = false
                    container.gesperrt.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    abfrageLaeuft = false
                    Log.w("Sperre", "Abbruch $errorCode: $errString")
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_prompt_title))
                .setSubtitle(getString(R.string.lock_prompt_subtitle))
                .setAllowedAuthenticators(art)
                .build(),
        )
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

@Composable
private fun SperrBildschirm(onEntsperren: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.lock_screen_text), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onEntsperren) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
