package de.belegblitz.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

class Converters {
    @TypeConverter fun datumZuLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longZuDatum(l: Long?): LocalDate? = l?.let { LocalDate.ofEpochDay(it) }
}

@Database(
    entities = [Person::class, Rechnung::class, Einreichung::class, Bescheid::class, BescheidPosition::class, VkbBeitrag::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun rechnungDao(): RechnungDao
    abstract fun vkbBeitragDao(): VkbBeitragDao
    abstract fun einreichungDao(): EinreichungDao
    abstract fun bescheidDao(): BescheidDao
    abstract fun sicherungDao(): SicherungDao
}