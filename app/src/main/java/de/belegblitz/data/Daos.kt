package de.belegblitz.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT COUNT(*) FROM Person")
    suspend fun anzahl(): Int

    @Query("SELECT * FROM Person ORDER BY id")
    fun alle(): Flow<List<Person>>

    @Query("SELECT id FROM Person ORDER BY id LIMIT 1")
    suspend fun ersteId(): Long?

    @Insert
    suspend fun einfuegen(person: Person): Long
}

@Dao
interface RechnungDao {
    @Query("SELECT * FROM Rechnung ORDER BY rechnungsdatum DESC, id DESC")
    fun alle(): Flow<List<Rechnung>>

    @Query("SELECT * FROM Rechnung WHERE id = :id")
    fun finde(id: Long): Flow<Rechnung?>

    @Query("SELECT * FROM Rechnung ORDER BY rechnungsdatum DESC, id DESC")
    suspend fun alleEinmal(): List<Rechnung>

    @Insert
    suspend fun einfuegen(rechnung: Rechnung): Long

    /** Issue #17: Hinweis setzen; null entfernt ihn. */
    @Query("UPDATE Rechnung SET hinweis = :hinweis WHERE id = :id")
    suspend fun hinweisSetzen(id: Long, hinweis: String?)
}

/** SR-7d1f8913431f: Einreichungen je Rechnung und Kasse. */
@Dao
interface EinreichungDao {
    /** SR-2b75d02ae8a8: live für die Übersicht. */
    @Query("SELECT * FROM Einreichung")
    fun alle(): Flow<List<Einreichung>>

    /** SR-aada699191a1: einmalige Abfrage für die tägliche Erinnerung. */
    @Query("SELECT * FROM Einreichung")
    suspend fun alleEinmal(): List<Einreichung>

    @Query("SELECT * FROM Einreichung WHERE rechnungId = :rechnungId")
    fun jeRechnung(rechnungId: Long): Flow<List<Einreichung>>

    @Query("SELECT * FROM Einreichung WHERE rechnungId = :rechnungId AND kasse = :kasse LIMIT 1")
    suspend fun finde(rechnungId: Long, kasse: de.belegblitz.domain.Kasse): Einreichung?

    @Query("SELECT * FROM Einreichung WHERE id = :id")
    suspend fun findeId(id: Long): Einreichung?

    @Insert
    suspend fun einfuegen(einreichung: Einreichung): Long

    @Update
    suspend fun aktualisieren(einreichung: Einreichung)
}

/** SR-3273b2e9382d, SR-93cb9591c1a1: Bescheide und ihre Positionen. */
@Dao
interface BescheidDao {
    @Insert
    suspend fun einfuegen(bescheid: Bescheid): Long

    /** Issue #17: Hinweis setzen; null entfernt ihn. */
    @Query("UPDATE Bescheid SET hinweis = :hinweis WHERE id = :id")
    suspend fun hinweisSetzen(id: Long, hinweis: String?)

    @Insert
    suspend fun positionEinfuegen(position: BescheidPosition): Long

    @Query("SELECT * FROM BescheidPosition WHERE einreichungId = :einreichungId")
    suspend fun positionen(einreichungId: Long): List<BescheidPosition>

    @Query("SELECT * FROM BescheidPosition WHERE rechnungId = :rechnungId")
    fun positionenJeRechnung(rechnungId: Long): Flow<List<BescheidPosition>>

    /** SR-9f0a44b25efd: zugeordnete Bescheide einer Rechnung (über ihre Positionen). */
    @Query("SELECT DISTINCT b.* FROM Bescheid b INNER JOIN BescheidPosition p ON p.bescheidId = b.id WHERE p.rechnungId = :rechnungId")
    fun jeRechnung(rechnungId: Long): Flow<List<Bescheid>>

    @Query("SELECT * FROM Bescheid")
    fun alle(): Flow<List<Bescheid>>

    /** SR-70a2f4475fe0: ein Bescheid für die Bescheid-Ansicht. */
    @Query("SELECT * FROM Bescheid WHERE id = :id")
    fun finde(id: Long): Flow<Bescheid?>

    /** SR-70a2f4475fe0: Positionen eines Bescheids (Rechnung und Betrag). */
    @Query("SELECT * FROM BescheidPosition WHERE bescheidId = :bescheidId ORDER BY id")
    fun positionenJeBescheid(bescheidId: Long): Flow<List<BescheidPosition>>

    /** SR-1d598be18236: Bescheide-Reiter, neueste zuerst. */
    @Query("SELECT * FROM Bescheid ORDER BY datum DESC, id DESC")
    fun alleNeuesteZuerst(): Flow<List<Bescheid>>

    /** SR-84e9478a8eb5: Suche ordnet Bescheide über ihre Positionen einer Rechnung zu. */
    @Query("SELECT * FROM BescheidPosition")
    fun allePositionen(): Flow<List<BescheidPosition>>
}

@Dao
interface VkbBeitragDao {
    @Query("SELECT * FROM VkbBeitrag ORDER BY gueltigAb DESC")
    fun alle(): Flow<List<VkbBeitrag>>

    @Insert
    suspend fun einfuegen(beitrag: VkbBeitrag): Long

    @Delete
    suspend fun loeschen(beitrag: VkbBeitrag)
}
/** SR-e334c167571f, SR-176f6f1f60df: Gesamtbestand lesen und ersetzen (Export/Wiederherstellung). */
@Dao
interface SicherungDao {
    @Query("SELECT * FROM Person ORDER BY id") suspend fun personen(): List<Person>
    @Query("SELECT * FROM Rechnung ORDER BY id") suspend fun rechnungen(): List<Rechnung>
    @Query("SELECT * FROM Einreichung ORDER BY id") suspend fun einreichungen(): List<Einreichung>
    @Query("SELECT * FROM Bescheid ORDER BY id") suspend fun bescheide(): List<Bescheid>
    @Query("SELECT * FROM BescheidPosition ORDER BY id") suspend fun positionen(): List<BescheidPosition>
    @Query("SELECT * FROM VkbBeitrag ORDER BY id") suspend fun beitraege(): List<VkbBeitrag>

    @Query("DELETE FROM BescheidPosition") suspend fun loeschePositionen()
    @Query("DELETE FROM Einreichung") suspend fun loescheEinreichungen()
    @Query("DELETE FROM Bescheid") suspend fun loescheBescheide()
    @Query("DELETE FROM Rechnung") suspend fun loescheRechnungen()
    @Query("DELETE FROM VkbBeitrag") suspend fun loescheBeitraege()
    @Query("DELETE FROM Person") suspend fun loeschePersonen()

    @Insert suspend fun einfuegenPersonen(l: List<Person>)
    @Insert suspend fun einfuegenRechnungen(l: List<Rechnung>)
    @Insert suspend fun einfuegenEinreichungen(l: List<Einreichung>)
    @Insert suspend fun einfuegenBescheide(l: List<Bescheid>)
    @Insert suspend fun einfuegenPositionen(l: List<BescheidPosition>)
    @Insert suspend fun einfuegenBeitraege(l: List<VkbBeitrag>)
}
