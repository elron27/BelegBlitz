package de.belegblitz.domain

/** Issue #17: freier Hinweis zu Rechnung oder Bescheid; ein leerer Text entfernt ihn. */
object Hinweis {
    /** Getrimmter Text, oder null (kein Hinweis), wenn nur Leerraum eingegeben wurde. */
    fun bereinigen(eingabe: String?): String? = eingabe?.trim()?.takeIf { it.isNotEmpty() }
}
