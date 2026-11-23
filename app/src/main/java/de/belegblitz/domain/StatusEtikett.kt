package de.belegblitz.domain

/** SR-17cd13ad9ebd: Art des farbigen Etiketts einer Einreichung (die UI ordnet jeder Art eine Farbe zu). */
enum class EtikettArt { ERFASST, EINGEREICHT, ERSTATTET, TEILWEISE, ABGELEHNT }

/** SR-17cd13ad9ebd: reine Logik für Status-Etiketten (Rechnungskarte) und Fortschrittsbalken (Übersicht). */
object StatusEtikett {
    /** Kriterium 1: jeder Status ergibt genau eine, je Status verschiedene Etikett-Art. */
    fun art(status: EinreichungStatus): EtikettArt = when (status) {
        EinreichungStatus.ERFASST -> EtikettArt.ERFASST
        EinreichungStatus.EINGEREICHT -> EtikettArt.EINGEREICHT
        EinreichungStatus.ERSTATTET -> EtikettArt.ERSTATTET
        EinreichungStatus.TEILWEISE_ERSTATTET -> EtikettArt.TEILWEISE
        EinreichungStatus.ABGELEHNT -> EtikettArt.ABGELEHNT
    }

    /** Kriterien 2 und 3: erstattet / eingereicht, bei eingereicht 0 ergibt 0, begrenzt auf 0..1. */
    fun fortschritt(erstattetCent: Long, eingereichtCent: Long): Float {
        if (eingereichtCent <= 0L) return 0f
        return (erstattetCent.toDouble() / eingereichtCent).coerceIn(0.0, 1.0).toFloat()
    }
}
