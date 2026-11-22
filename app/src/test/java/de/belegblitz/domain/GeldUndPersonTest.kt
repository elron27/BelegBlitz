package de.belegblitz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeldUndPersonTest {
    @Test fun `Betrag parsen`() {
        assertEquals(1250L, Geld.parseCent("12,50"))
        assertEquals(1250L, Geld.parseCent("12.5"))
        assertEquals(1200L, Geld.parseCent("12"))
        assertNull(Geld.parseCent("abc"))
        assertNull(Geld.parseCent("1,234"))
    }

    @Test fun `Betrag formatieren`() = assertEquals("1.234,50 €", Geld.formatEuro(123450))

    @Test fun `SR-4e84512d52d0 AC2 beim ersten Start wird genau eine Person angelegt`() {
        assertTrue(StandardPerson.mussAngelegtWerden(0))
        assertFalse(StandardPerson.mussAngelegtWerden(1))
    }
}