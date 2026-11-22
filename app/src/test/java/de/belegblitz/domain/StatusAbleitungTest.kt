package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusAbleitungTest {
    @Test fun `SR-40de3efa693e AC1 Erstattung null ergibt Abgelehnt`() =
        assertEquals(EinreichungStatus.ABGELEHNT, StatusAbleitung.ausErstattung(10000, 0))

    @Test fun `SR-40de3efa693e AC2 Erstattung kleiner als eingereicht ergibt Teilweise erstattet`() =
        assertEquals(EinreichungStatus.TEILWEISE_ERSTATTET, StatusAbleitung.ausErstattung(10000, 4999))

    @Test fun `SR-40de3efa693e AC3 Erstattung gleich eingereicht ergibt Erstattet`() =
        assertEquals(EinreichungStatus.ERSTATTET, StatusAbleitung.ausErstattung(10000, 10000))
}