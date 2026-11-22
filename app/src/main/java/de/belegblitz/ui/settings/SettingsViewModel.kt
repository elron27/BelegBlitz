package de.belegblitz.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.belegblitz.AppContainer
import de.belegblitz.data.VkbBeitrag
import de.belegblitz.data.export.SicherungsErgebnis
import kotlinx.coroutines.flow.MutableStateFlow
import de.belegblitz.domain.Geld
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

val DatumFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.db.vkbBeitragDao()

    private val einstellungen = container.einstellungen
    val updater = container.updater
    private val sicherung = container.sicherung

    /** SR-e334c167571f, SR-176f6f1f60df: Stand von Export/Wiederherstellung für die Anzeige. */
    sealed interface SicherungsStand {
        data object Leerlauf : SicherungsStand
        data class Laeuft(val export: Boolean, val erledigt: Int, val gesamt: Int) : SicherungsStand
        data class Fertig(val export: Boolean, val ergebnis: SicherungsErgebnis) : SicherungsStand
        data class Fehler(val export: Boolean, val meldung: String?) : SicherungsStand
    }
    private val _sicherungsStand = MutableStateFlow<SicherungsStand>(SicherungsStand.Leerlauf)
    val sicherungsStand: StateFlow<SicherungsStand> = _sicherungsStand

    fun exportieren(uri: Uri) = starten(true, uri)

    /** true, wenn schon Daten da sind: dann erst nachfragen und danach [wiederherstellen] aufrufen. */
    suspend fun bestandVorhanden() = sicherung.bestandVorhanden()

    fun wiederherstellen(uri: Uri) = starten(false, uri)

    private fun starten(export: Boolean, uri: Uri) {
        if (_sicherungsStand.value is SicherungsStand.Laeuft) return
        _sicherungsStand.value = SicherungsStand.Laeuft(export, 0, 1)
        viewModelScope.launch {
            val fortschritt = { e: Int, g: Int -> _sicherungsStand.value = SicherungsStand.Laeuft(export, e, g) }
            _sicherungsStand.value = try {
                SicherungsStand.Fertig(
                    export,
                    if (export) sicherung.exportieren(uri, fortschritt) else sicherung.wiederherstellen(uri, fortschritt),
                )
            } catch (e: Exception) { // bereits im Dienst geloggt
                SicherungsStand.Fehler(export, e.message)
            }
        }
    }
    val sperreAktiv = einstellungen.sperreAktiv
    val wartezeitWochen = einstellungen.wartezeitWochen
    val updateOrdner = einstellungen.updateOrdner

    fun setSperreAktiv(wert: Boolean) = einstellungen.setSperreAktiv(wert)
    fun setWartezeitWochen(wochen: Int) = einstellungen.setWartezeitWochen(wochen)
    fun setUpdateOrdner(uri: String) = einstellungen.setUpdateOrdner(uri)

    val beitraege: StateFlow<List<VkbBeitrag>> =
        dao.alle().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Gibt true zurück, wenn der Eintrag gültig war und gespeichert wird. */
    fun hinzufuegen(betragText: String, datumText: String): Boolean {
        val cent = Geld.parseCent(betragText)
        val datum = runCatching { LocalDate.parse(datumText.trim(), DatumFormat) }.getOrNull()
        if (cent == null || cent <= 0 || datum == null) return false
        viewModelScope.launch { dao.einfuegen(VkbBeitrag(gueltigAb = datum, monatsbetragCent = cent)) }
        return true
    }

    fun loeschen(beitrag: VkbBeitrag) {
        viewModelScope.launch { dao.loeschen(beitrag) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsViewModel(container) } }
    }
}