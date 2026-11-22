package de.belegblitz.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT COUNT(*) FROM Person")
    suspend fun anzahl(): Int

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

    @Insert
    suspend fun einfuegen(rechnung: Rechnung): Long
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