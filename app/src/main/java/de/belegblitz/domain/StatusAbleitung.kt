package de.belegblitz.domain

/** SR-40de3efa693e: Der Status folgt automatisch aus dem erfassten Erstattungsbetrag. */
object StatusAbleitung {
    fun ausErstattung(eingereichtCent: Long, erstattetCent: Long): EinreichungStatus {
        require(erstattetCent >= 0) { "Erstattung darf nicht negativ sein" }
        return when {
            erstattetCent == 0L -> EinreichungStatus.ABGELEHNT
            erstattetCent < eingereichtCent -> EinreichungStatus.TEILWEISE_ERSTATTET
            else -> EinreichungStatus.ERSTATTET
        }
    }
}