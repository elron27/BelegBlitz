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
}

/** SR-7d1f8913431f: Einreichungen je Rechnung und Kasse. */
@Dao
interface EinreichungDao {
    /** SR-2b75d02ae8a8: live für die Übersicht. */
    @Query("SELECT * FROM Einreichung")
    fun alle(): Flow<List<Einreichung>>

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

    @Insert
    suspend fun positionEinfuegen(position: BescheidPosition): Long

    @Query("SELECT * FROM BescheidPosition WHERE einreichungId = :einreichungId")
    suspend fun positionen(einreichungId: Long): List<BescheidPosition>

    @Query("SELECT * FROM BescheidPosition WHERE rechnungId = :rechnungId")
    fun positionenJeRechnung(rechnungId: Long): Flow<List<BescheidPosition>>

    @Query("SELECT * FROM Bescheid")
    fun alle(): Flow<List<Bescheid>>

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