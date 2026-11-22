package de.belegblitz.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.belegblitz.domain.EinreichungStatus
import de.belegblitz.domain.Kasse
import java.time.LocalDate

/** SR-4e84512d52d0: versicherte Person (Stufe 1: genau eine). */
@Entity
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("personId")],
)
data class Rechnung(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val praxis: String,
    val fachrichtung: String,
    val rechnungsdatum: LocalDate,
    val betragCent: Long,
    val titel: String,
    val pdfPfad: String?,
    val ocrText: String?,
    val erstellt: Long,
)

/** SR-7d1f8913431f: Einreichung je Rechnung und Kasse. */
@Entity(
    foreignKeys = [ForeignKey(Rechnung::class, ["id"], ["rechnungId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("rechnungId")],
)
data class Einreichung(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rechnungId: Long,
    val kasse: Kasse,
    val einreichungsdatum: LocalDate?,
    val betragCent: Long,
    val status: EinreichungStatus,
    val erstatteterBetragCent: Long?,
    val hinweis: String?,
    val dokumentPfad: String?,
)

@Entity
data class Bescheid(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kasse: Kasse,
    val datum: LocalDate,
    val dokumentPfad: String?,
    val ocrText: String?,
)

/** Zuordnung eines Bescheids zu Rechnung/Einreichung mit Betrag je Position. */
@Entity(
    foreignKeys = [
        ForeignKey(Bescheid::class, ["id"], ["bescheidId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Rechnung::class, ["id"], ["rechnungId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Einreichung::class, ["id"], ["einreichungId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("bescheidId"), Index("rechnungId"), Index("einreichungId")],
)
data class BescheidPosition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bescheidId: Long,
    val rechnungId: Long,
    val einreichungId: Long?,
    val betragCent: Long,
)

/** SR-984a2c74ebc1: Eintrag im VKB-Beitragsverlauf. */
@Entity
data class VkbBeitrag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gueltigAb: LocalDate,
    val monatsbetragCent: Long,
)