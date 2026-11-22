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

    @Insert
    suspend fun einfuegen(person: Person): Long
}

@Dao
interface RechnungDao {
    @Query("SELECT * FROM Rechnung ORDER BY rechnungsdatum DESC, id DESC")
    fun alle(): Flow<List<Rechnung>>

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