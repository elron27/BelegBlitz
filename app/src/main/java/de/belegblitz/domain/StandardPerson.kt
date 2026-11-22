package de.belegblitz.domain

/** SR-4e84512d52d0: Beim ersten Start gibt es genau eine Person. */
object StandardPerson {
    fun mussAngelegtWerden(vorhandenePersonen: Int): Boolean = vorhandenePersonen == 0
}