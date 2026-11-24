package de.belegblitz.domain

import java.time.LocalDate

/** Erstattungsstand einer Einreichung nach Auswertung der Bescheid-Positionen. */
data class Erstattungsstand(val erstattetCent: Long, val status: EinreichungStatus)

/** Issue #16: neu berechneter Zustand einer Einreichung (Erstattung null = noch nichts erstattet). */
data class Einreichungsstand(val erstattetCent: Long?, val status: EinreichungStatus)

/** Reine Regeln rund um Einreichungen (ohne Room/Android). */
object Einreichungsregeln {

    /** SR-9b67f3ef174c: "Eingereicht" ist nur sinnvoll, solange noch nichts eingereicht oder erstattet wurde. */
    fun kannAlsEingereichtMarkiert(status: EinreichungStatus) = status == EinreichungStatus.ERFASST

    /**
     * SR-9b67f3ef174c: ein Tipp setzt den Status Eingereicht; das Einreichungsdatum ist heute,
     * solange der Nutzer kein anderes gewählt hat.
     */
    fun alsEingereicht(heute: LocalDate, gewaehlt: LocalDate? = null): Pair<EinreichungStatus, LocalDate> =
        EinreichungStatus.EINGEREICHT to (gewaehlt ?: heute)

    /**
     * SR-93cb9591c1a1 (Kriterium 2) und SR-40de3efa693e: eine Rechnung kann Beträge aus mehreren Bescheiden
     * erhalten; Summe aller Positionen bestimmt die Erstattung und daraus den Status.
     */
    fun stand(eingereichtCent: Long, positionenCent: List<Long>): Erstattungsstand {
        val summe = positionenCent.sum()
        return Erstattungsstand(summe, StatusAbleitung.ausErstattung(eingereichtCent, summe))
    }

    /**
     * SR-7d1f8913431f: Wird der Betrag einer Einreichung geändert, folgt der Status der bereits erfassten
     * Erstattung (SR-40de3efa693e); ohne Erstattung bleibt der Status.
     */
    fun statusNachBetragsaenderung(status: EinreichungStatus, neuerBetragCent: Long, erstattetCent: Long?): EinreichungStatus =
        if (erstattetCent == null) status else StatusAbleitung.ausErstattung(neuerBetragCent, erstattetCent)

    /** SR-bb429d971190 siehe [ManuelleErstattung]: Erstattung von Hand nur für bereits eingereichte Einreichungen. */
    fun kannErstattungEintragen(status: EinreichungStatus) = status != EinreichungStatus.ERFASST

    /** SR-d2f03abf6d13 (Kriterium 1): bei der DAK abgelehnt oder nur teilweise erstattet -> VKB anbieten. */
    fun vkbAnbieten(dakStatus: EinreichungStatus) =
        dakStatus == EinreichungStatus.ABGELEHNT || dakStatus == EinreichungStatus.TEILWEISE_ERSTATTET

    /**
     * SR-d2f03abf6d13 (Kriterium 2): vorgeschlagener VKB-Betrag = nicht erstatteter Rest der DAK-Einreichung;
     * null, wenn keine Nacheinreichung angeboten wird.
     */
    fun vkbRestbetrag(dakStatus: EinreichungStatus, dakEingereichtCent: Long, dakErstattetCent: Long?): Long? =
        if (vkbAnbieten(dakStatus)) (dakEingereichtCent - (dakErstattetCent ?: 0L)).coerceAtLeast(0L) else null

    /**
     * Issue #16: Neuberechnung einer Einreichung aus ihren Positionen (nach Betragsänderung, Bearbeiten oder Löschen
     * eines Bescheids).
     * - Es bleiben Positionen: Erstattung = Summe, Status daraus (wie [stand]).
     * - Keine Position mehr, aber gelöschte, deren Summe der Erstattung entspricht: Rückfall auf Eingereicht
     *   (Einreichungsdatum gesetzt) bzw. Erfasst, Erstattung null.
     * - Sonst war die Erstattung von Hand eingetragen und bleibt; der Status folgt nur dem neuen Betrag.
     * @param verbleibendCent Beträge der noch vorhandenen Positionen (neue Werte)
     * @param entferntCent Beträge der entfernten Positionen (Werte vor dem Entfernen)
     */
    fun neuBerechnen(
        betragCent: Long, einreichungsdatum: LocalDate?, status: EinreichungStatus, erstattetCent: Long?,
        verbleibendCent: List<Long>, entferntCent: List<Long> = emptyList(),
    ): Einreichungsstand {
        if (verbleibendCent.isNotEmpty()) {
            val s = stand(betragCent, verbleibendCent)
            return Einreichungsstand(s.erstattetCent, s.status)
        }
        if (entferntCent.isNotEmpty() && erstattetCent == entferntCent.sum()) {
            return Einreichungsstand(null, if (einreichungsdatum != null) EinreichungStatus.EINGEREICHT else EinreichungStatus.ERFASST)
        }
        return Einreichungsstand(erstattetCent, statusNachBetragsaenderung(status, betragCent, erstattetCent))
    }

    /** SR-a6fc7a6cdbe1: neuen Bescheid-Hinweis an den vorhandenen anhängen, ohne Doppelungen. */
    fun hinweisZusammenfuehren(alt: String?, neu: String?): String? {
        val a = alt?.trim().orEmpty()
        val n = neu?.trim().orEmpty()
        return when {
            n.isEmpty() -> alt?.takeIf { it.isNotBlank() }
            a.isEmpty() -> n
            a.contains(n) -> a
            else -> "$a\n$n"
        }
    }
}
