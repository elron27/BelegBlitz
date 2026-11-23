package de.belegblitz.domain

import java.time.LocalDate
import java.time.YearMonth

/** Eine Einreichung mit den für die Auswertung nötigen Angaben (Jahr = Jahr des Rechnungsdatums). */
data class AuswertungsZeile(
    val personId: Long,
    val jahr: Int,
    val kasse: Kasse,
    val status: EinreichungStatus,
    val eingereichtCent: Long,
    val erstattetCent: Long?,
    /** SR-75f859f5adc5: Rechnung, zu der die Einreichung gehört (für die gefilterte Rechnungsliste). */
    val rechnungId: Long = 0,
)

/** SR-d840886a42a1: nichtErstattetCent = Rest abgelehnter bzw. teilweise erstatteter Einreichungen. */
data class Kassensumme(
    val eingereichtCent: Long,
    val erstattetCent: Long,
    val offenCent: Long,
    val nichtErstattetCent: Long = 0,
)

/** SR-2b75d02ae8a8, SR-d109d26f3f86: Summen je Kasse und Filter (reine Logik, ohne Android). */
object Auswertung {

    /** SR-d109d26f3f86: null = kein Filter. */
    fun filtern(zeilen: List<AuswertungsZeile>, jahr: Int?, kasse: Kasse?, personId: Long?): List<AuswertungsZeile> =
        zeilen.filter {
            (jahr == null || it.jahr == jahr) && (kasse == null || it.kasse == kasse) &&
                (personId == null || it.personId == personId)
        }

    /** Jahre, für die es Einreichungen gibt, absteigend. */
    fun jahre(zeilen: List<AuswertungsZeile>): List<Int> = zeilen.map { it.jahr }.distinct().sortedDescending()

    /**
     * SR-2b75d02ae8a8: eingereicht = alle Einreichungen, die nicht mehr nur erfasst sind; erstattet = erhaltene
     * Beträge; offen = noch ausstehend (eingereicht oder teilweise erstattet), abgelehnte gelten als erledigt.
     */
    fun summe(zeilen: List<AuswertungsZeile>): Kassensumme {
        val eingereicht = zeilen.filter { it.status != EinreichungStatus.ERFASST }
        val eingereichtCent = eingereicht.sumOf { it.eingereichtCent }
        val erstattetCent = eingereicht.sumOf { it.erstattetCent ?: 0L }
        val offen = eingereicht.filter {
            it.status == EinreichungStatus.EINGEREICHT || it.status == EinreichungStatus.TEILWEISE_ERSTATTET
        }.sumOf { (it.eingereichtCent - (it.erstattetCent ?: 0L)).coerceAtLeast(0L) }
        // SR-d840886a42a1: eingereicht minus erstattet nur bei abgelehnt/teilweise erstattet, nie negativ
        val nichtErstattet = eingereicht.filter { it.status in NICHT_ERSTATTET_STATUS }
            .sumOf { (it.eingereichtCent - (it.erstattetCent ?: 0L)).coerceAtLeast(0L) }
        return Kassensumme(eingereichtCent, erstattetCent, offen, nichtErstattet)
    }

    private val NICHT_ERSTATTET_STATUS = setOf(EinreichungStatus.ABGELEHNT, EinreichungStatus.TEILWEISE_ERSTATTET)

    /** SR-75f859f5adc5: IDs der Rechnungen, von denen mindestens eine Einreichung zum Filter passt. */
    fun rechnungIds(zeilen: List<AuswertungsZeile>, filter: RechnungsFilter): Set<Long> =
        zeilen.filter { filter.passt(it) }.map { it.rechnungId }.toSet()

    /** Eine Summe je Kasse (beide Kassen immer vorhanden). */
    fun jeKasse(zeilen: List<AuswertungsZeile>): Map<Kasse, Kassensumme> =
        Kasse.entries.associateWith { k -> summe(zeilen.filter { it.kasse == k }) }

    /** SR-d109d26f3f86 Kriterium 2: Personen-Filter erst bei mehr als einer Person. */
    fun personenFilterSichtbar(anzahlPersonen: Int) = anzahlPersonen > 1
}

/** SR-5b4224df66fd: Jahresbilanz der VKB. */
data class VkbBilanz(
    val jahr: Int,
    val beitraegeCent: Long,
    val erstattetCent: Long,
    /** Erstattungen minus Beiträge; negativ = Beiträge noch nicht hereingeholt. */
    val differenzCent: Long,
    val hereingeholt: Boolean,
    /** Anzahl der gezählten Monate (laufendes Jahr: nur bis zum aktuellen Monat). */
    val monate: Int,
    val laufendesJahr: Boolean,
)

object VkbJahresbilanz {
    fun berechnen(
        jahr: Int,
        verlauf: List<BeitragsEintrag>,
        vkbErstattungenCent: Long,
        heute: LocalDate,
    ): VkbBilanz {
        val laufend = jahr == heute.year
        val monate = when {
            jahr < heute.year -> 12
            laufend -> heute.monthValue
            else -> 0
        }
        val beitraege = (1..monate).sumOf { Beitragsverlauf.fuerMonat(verlauf, YearMonth.of(jahr, it)) ?: 0L }
        val differenz = vkbErstattungenCent - beitraege
        return VkbBilanz(jahr, beitraege, vkbErstattungenCent, differenz, differenz >= 0, monate, laufend)
    }
}
