package de.belegblitz

import android.os.Bundle
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
        setContent {
            BelegBlitzTheme { AppNavigation(container) }
        }
    }
}