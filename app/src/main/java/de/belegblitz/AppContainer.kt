package de.belegblitz

import android.content.Context
import androidx.room.Room
import de.belegblitz.data.AppDatabase
import de.belegblitz.data.Person
import de.belegblitz.domain.StandardPerson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manuelle Abhängigkeitsverwaltung (kein Hilt). */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "belegblitz.db").build()

    init {
        // SR-4e84512d52d0: beim ersten Start genau eine Person anlegen
        scope.launch {
            if (StandardPerson.mussAngelegtWerden(db.personDao().anzahl())) {
                db.personDao().einfuegen(Person(name = context.getString(R.string.default_person_name)))
            }
        }
    }
}