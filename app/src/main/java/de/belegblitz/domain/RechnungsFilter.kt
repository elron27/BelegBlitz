package de.belegblitz.domain

/** SR-75f859f5adc5: Status-Gruppe der klickbaren Übersichtszeilen. */
enum class StatusGruppe { EINGEREICHT, ERSTATTET, OFFEN, NICHT_ERSTATTET }

/** SR-75f859f5adc5: Filter der Rechnungsliste (jahr null = alle Jahre). */
data class RechnungsFilter(val kasse: Kasse, val jahr: Int?, val gruppe: StatusGruppe) {

    /** Eine Einreichung passt, wenn Kasse, Jahr und Status-Gruppe stimmen. */
    fun passt(z: AuswertungsZeile): Boolean =
        z.kasse == kasse && (jahr == null || z.jahr == jahr) && when (gruppe) {
            StatusGruppe.EINGEREICHT -> z.status != EinreichungStatus.ERFASST
            StatusGruppe.ERSTATTET -> (z.erstattetCent ?: 0L) > 0L
            StatusGruppe.OFFEN ->
                z.status == EinreichungStatus.EINGEREICHT || z.status == EinreichungStatus.TEILWEISE_ERSTATTET
            StatusGruppe.NICHT_ERSTATTET ->
                z.status == EinreichungStatus.ABGELEHNT || z.status == EinreichungStatus.TEILWEISE_ERSTATTET
        }
}
