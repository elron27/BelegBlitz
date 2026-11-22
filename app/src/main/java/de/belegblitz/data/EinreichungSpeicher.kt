package de.belegblitz.data

import androidx.room.withTransaction
import de.belegblitz.domain.EinreichungStatus
import de.belegblitz.domain.Einreichungsregeln
import de.belegblitz.domain.Kasse
import java.time.LocalDate

/** SR-7d1f8913431f, SR-3273b2e9382d, SR-93cb9591c1a1: schreibende Abläufe rund um Einreichungen und Bescheide. */
class EinreichungSpeicher(private val db: AppDatabase) {

    /** Legt die Einreichung an; sie verweist auf das PDF der Rechnung als eingereichtes Dokument (Kriterium 3). */
    suspend fun anlegen(rechnung: Rechnung, kasse: Kasse, betragCent: Long): Long =
        db.einreichungDao().finde(rechnung.id, kasse)?.id ?: db.einreichungDao().einfuegen(
            Einreichung(
                rechnungId = rechnung.id, kasse = kasse, einreichungsdatum = null, betragCent = betragCent,
                status = EinreichungStatus.ERFASST, erstatteterBetragCent = null, hinweis = null,
                dokumentPfad = rechnung.pdfPfad,
            ),
        )

    /** SR-9b67f3ef174c: ein Tipp -> Eingereicht, Datum heute. */
    suspend fun alsEingereichtMarkieren(e: Einreichung, heute: LocalDate = LocalDate.now()) {
        if (!Einreichungsregeln.kannAlsEingereichtMarkiert(e.status)) return
        val (status, datum) = Einreichungsregeln.alsEingereicht(heute, e.einreichungsdatum)
        db.einreichungDao().aktualisieren(e.copy(status = status, einreichungsdatum = datum))
    }

    suspend fun datumAendern(e: Einreichung, datum: LocalDate?) =
        db.einreichungDao().aktualisieren(e.copy(einreichungsdatum = datum))

    /** SR-a6fc7a6cdbe1 (Kriterium 2): Hinweistext bearbeiten oder selbst eintragen. */
    suspend fun hinweisAendern(e: Einreichung, text: String) =
        db.einreichungDao().aktualisieren(e.copy(hinweis = text.trim().ifEmpty { null }))

    /**
     * SR-3273b2e9382d, SR-93cb9591c1a1, SR-40de3efa693e, SR-a6fc7a6cdbe1: speichert den Bescheid, verknüpft jede Position
     * mit Rechnung und Einreichung (legt sie bei Bedarf an), leitet den Status aus der Summe aller Positionen ab.
     * @param positionen Paare (rechnungId, Erstattung in Cent)
     */
    suspend fun bescheidSpeichern(bescheid: Bescheid, positionen: List<Pair<Long, Long>>, hinweis: String?) {
        db.withTransaction {
            val bescheidId = db.bescheidDao().einfuegen(bescheid)
            val rechnungen = db.rechnungDao().alleEinmal().associateBy { it.id }
            val betroffen = linkedSetOf<Long>()
            for ((rechnungId, cent) in positionen) {
                val rechnung = rechnungen[rechnungId] ?: continue
                val einreichungId = anlegen(rechnung, bescheid.kasse, rechnung.betragCent)
                db.bescheidDao().positionEinfuegen(BescheidPosition(0, bescheidId, rechnungId, einreichungId, cent))
                betroffen += einreichungId
            }
            for (einreichungId in betroffen) {
                val e = db.einreichungDao().findeId(einreichungId) ?: continue
                val stand = Einreichungsregeln.stand(e.betragCent, db.bescheidDao().positionen(einreichungId).map { it.betragCent })
                db.einreichungDao().aktualisieren(
                    e.copy(
                        erstatteterBetragCent = stand.erstattetCent, status = stand.status,
                        hinweis = Einreichungsregeln.hinweisZusammenfuehren(e.hinweis, hinweis),
                    ),
                )
            }
        }
    }
}
