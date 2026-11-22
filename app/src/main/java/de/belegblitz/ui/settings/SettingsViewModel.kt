package de.belegblitz.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.belegblitz.AppContainer
import de.belegblitz.data.VkbBeitrag
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