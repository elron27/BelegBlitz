package de.belegblitz.data

import android.content.Context
import de.belegblitz.domain.Erinnerung
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App-Einstellungen in SharedPreferences (keine Room-Änderung nötig). */
class Einstellungen(context: Context) {
    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    private val _sperreAktiv = MutableStateFlow(prefs.getBoolean(SPERRE, false))
    /** SR-c43e8d9ab0a8 */
    val sperreAktiv: StateFlow<Boolean> = _sperreAktiv

    private val _wartezeitWochen =
        MutableStateFlow(Erinnerung.begrenzeWochen(prefs.getInt(WARTEZEIT, Erinnerung.VORGABE_WOCHEN)))
    /** SR-aada699191a1, Vorgabe 4 Wochen */
    val wartezeitWochen: StateFlow<Int> = _wartezeitWochen

    private val _updateOrdner = MutableStateFlow(prefs.getString(UPDATE_ORDNER, null))
    /** Gewählter Update-Ordner (Tree-URI als Text) */
    val updateOrdner: StateFlow<String?> = _updateOrdner

    fun setSperreAktiv(wert: Boolean) {
        prefs.edit().putBoolean(SPERRE, wert).apply()
        _sperreAktiv.value = wert
    }

    fun setWartezeitWochen(wochen: Int) {
        val w = Erinnerung.begrenzeWochen(wochen)
        prefs.edit().putInt(WARTEZEIT, w).apply()
        _wartezeitWochen.value = w
    }

    fun setUpdateOrdner(uri: String) {
        prefs.edit().putString(UPDATE_ORDNER, uri).apply()
        _updateOrdner.value = uri
    }

    private companion object {
        const val SPERRE = "sperre_aktiv"
        const val WARTEZEIT = "wartezeit_wochen"
        const val UPDATE_ORDNER = "update_ordner"
    }
}
