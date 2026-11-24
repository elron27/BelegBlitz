package de.belegblitz.data

import androidx.room.withTransaction
import de.belegblitz.domain.EinreichungStatus
import de.belegblitz.domain.Hinweis
import de.belegblitz.domain.RechnungEingabe
import de.belegblitz.domain.Einreichungsregeln
import de.belegblitz.domain.Kasse
import de.belegblitz.domain.ManuelleErstattung
import java.time.LocalDate

/** SR-7d1f8913431f, SR-3273b2e9382d, SR-93cb9591c1a1: schreibende Abläufe rund um Einreichungen und Bescheide. */
class EinreichungSpeicher(
    private val db: AppDatabase,
    /** Issue #16: entfernt eine PDF-Datei (Name im PDF-Ordner); wird erst nach dem Festschreiben aufgerufen. */
    private val dateiLoeschen: (String) -> Unit = {},
) {

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

    /** SR-7d1f8913431f: Betrag der Einreichung bearbeiten; Status folgt einer vorhandenen Erstattung. */
    suspend fun betragAendern(e: Einreichung, betragCent: Long) =
        db.einreichungDao().aktualisieren(
            e.copy(
                betragCent = betragCent,
                status = Einreichungsregeln.statusNachBetragsaenderung(e.status, betragCent, e.erstatteterBetragCent),
            ),
        )

    /** SR-bb429d971190: Erstattung von Hand setzen (auch 0); Status folgt dem Betrag. Nicht für Status Erfasst. */
    suspend fun erstattungManuell(e: Einreichung, erstattetCent: Long) {
        if (!Einreichungsregeln.kannErstattungEintragen(e.status)) return
        val r = ManuelleErstattung.uebernehmen(e.betragCent, erstattetCent)
        db.einreichungDao().aktualisieren(e.copy(erstatteterBetragCent = r.erstattetCent, status = r.status))
    }

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
            neuBerechnen(betroffen, hinweis = hinweis)
        }
    }

    /**
     * Issue #16: berechnet Erstattung und Status der Einreichungen aus ihren verbleibenden Positionen neu
     * ([Einreichungsregeln.neuBerechnen]). Muss in einer Transaktion laufen.
     * @param entfernt je Einreichung die Beträge der soeben entfernten Positionen (für den Rückfall)
     * @param hinweis Bescheid-Hinweis, der an den Einreichungs-Hinweis angehängt wird (null = unverändert)
     */
    private suspend fun neuBerechnen(
        einreichungIds: Collection<Long>, entfernt: Map<Long, List<Long>> = emptyMap(), hinweis: String? = null,
    ) {
        for (einreichungId in einreichungIds) {
            val e = db.einreichungDao().findeId(einreichungId) ?: continue
            val verbleibend = db.bescheidDao().positionen(einreichungId).map { it.betragCent }
            val stand = Einreichungsregeln.neuBerechnen(
                e.betragCent, e.einreichungsdatum, e.status, e.erstatteterBetragCent, verbleibend, entfernt[einreichungId].orEmpty(),
            )
            db.einreichungDao().aktualisieren(
                e.copy(
                    erstatteterBetragCent = stand.erstattetCent, status = stand.status,
                    hinweis = if (hinweis == null) e.hinweis else Einreichungsregeln.hinweisZusammenfuehren(e.hinweis, hinweis),
                ),
            )
        }
    }

    /**
     * Issue #16: Rechnung bearbeiten. Der Titel wird neu abgeleitet, der PDF-Name bleibt. Ändert sich der Betrag,
     * ziehen alle Einreichungen der Rechnung (Betrag, Status) nach.
     */
    suspend fun rechnungAendern(rechnung: Rechnung, eingabe: RechnungEingabe) {
        db.withTransaction {
            db.rechnungDao().aktualisieren(
                rechnung.copy(
                    praxis = eingabe.praxis, fachrichtung = eingabe.fachrichtung, rechnungsdatum = eingabe.datum,
                    betragCent = eingabe.betragCent, titel = eingabe.titel,
                ),
            )
            if (eingabe.betragCent != rechnung.betragCent) {
                val einreichungen = db.einreichungDao().jeRechnungEinmal(rechnung.id)
                einreichungen.forEach { db.einreichungDao().aktualisieren(it.copy(betragCent = eingabe.betragCent)) }
                neuBerechnen(einreichungen.map { it.id })
            }
        }
    }

    /**
     * Issue #16: Rechnung löschen samt PDF. Einreichungen und Bescheid-Positionen entfallen (Kaskade);
     * Bescheide ohne verbleibende Positionen bleiben stehen.
     */
    suspend fun rechnungLoeschen(rechnung: Rechnung) {
        db.withTransaction { db.rechnungDao().loeschen(rechnung) }
        rechnung.pdfPfad?.let(dateiLoeschen)
    }

    /**
     * Issue #16: Bescheid bearbeiten: Kasse, Datum, Hinweis, Beträge einzelner Positionen (Positions-ID -> Cent) und
     * Positionen entfernen (keine neuen). Bei Kassenwechsel hängen die Positionen an der Einreichung der neuen Kasse
     * (bei Bedarf angelegt); beide Kassen werden neu berechnet.
     */
    suspend fun bescheidAendern(
        bescheidId: Long, kasse: Kasse, datum: LocalDate, hinweis: String?,
        betraege: Map<Long, Long>, entfernt: Set<Long>,
    ) {
        db.withTransaction {
            val alt = db.bescheidDao().findeEinmal(bescheidId) ?: return@withTransaction
            val rechnungen = db.rechnungDao().alleEinmal().associateBy { it.id }
            val betroffen = linkedSetOf<Long>()
            val entferntJe = mutableMapOf<Long, MutableList<Long>>()
            fun verliert(p: BescheidPosition) {
                val id = p.einreichungId ?: return
                betroffen += id
                entferntJe.getOrPut(id) { mutableListOf() } += p.betragCent
            }
            val positionen = db.bescheidDao().positionenJeBescheidEinmal(bescheidId)
            for (p in positionen) {
                if (p.id in entfernt) { verliert(p); continue }
                var einreichungId = p.einreichungId
                if (kasse != alt.kasse) {
                    verliert(p)
                    rechnungen[p.rechnungId]?.let { einreichungId = anlegen(it, kasse, it.betragCent) }
                }
                einreichungId?.let { betroffen += it }
                val betrag = betraege[p.id] ?: p.betragCent
                if (betrag != p.betragCent || einreichungId != p.einreichungId) {
                    db.bescheidDao().positionAktualisieren(p.copy(betragCent = betrag, einreichungId = einreichungId))
                }
            }
            db.bescheidDao().positionenLoeschen(positionen.filter { it.id in entfernt }.map { it.id })
            db.bescheidDao().aktualisieren(alt.copy(kasse = kasse, datum = datum, hinweis = Hinweis.bereinigen(hinweis)))
            neuBerechnen(betroffen, entferntJe)
        }
    }

    /** Issue #16: Bescheid löschen samt PDF; seine Einreichungen werden neu berechnet (Rückfall ohne Positionen). */
    suspend fun bescheidLoeschen(bescheidId: Long) {
        var datei: String? = null
        db.withTransaction {
            val alt = db.bescheidDao().findeEinmal(bescheidId) ?: return@withTransaction
            datei = alt.dokumentPfad
            val positionen = db.bescheidDao().positionenJeBescheidEinmal(bescheidId)
            val entferntJe = mutableMapOf<Long, MutableList<Long>>()
            positionen.forEach { p -> p.einreichungId?.let { entferntJe.getOrPut(it) { mutableListOf() } += p.betragCent } }
            db.bescheidDao().positionenLoeschen(positionen.map { it.id })
            db.bescheidDao().loeschen(alt)
            neuBerechnen(entferntJe.keys, entferntJe)
        }
        datei?.let(dateiLoeschen)
    }
}
