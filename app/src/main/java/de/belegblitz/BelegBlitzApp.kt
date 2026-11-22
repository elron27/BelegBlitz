package de.belegblitz

import android.app.Application

class BelegBlitzApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // SR-aada699191a1: tägliche Prüfung auf Einreichungen ohne Bescheid
        de.belegblitz.data.ErinnerungsWorker.planen(this)
    }
}