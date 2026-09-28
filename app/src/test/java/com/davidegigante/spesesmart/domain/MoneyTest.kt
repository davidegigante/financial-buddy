package com.davidegigante.spesesmart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun format() {
        assertEquals("12,50 €", Money.format(1_250))
        assertEquals("0,05 €", Money.format(5))
        assertEquals("1.200,00 €", Money.format(120_000))
        assertEquals("-50,00 €", Money.format(-5_000))
        assertEquals("1.234.567,89 €", Money.format(123_456_789))
    }

    @Test
    fun parse() {
        assertEquals(1_200L, Money.parse("12"))
        assertEquals(1_250L, Money.parse("12,5"))
        assertEquals(1_250L, Money.parse("12,50"))
        assertEquals(1_250L, Money.parse("12.50"))
        assertEquals(120_050L, Money.parse("1.200,50"))
        assertEquals(120_000L, Money.parse("1.200"))
        assertEquals(320L, Money.parse("€ 3,20"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("12,345"))
    }

    @Test
    fun findAmount() {
        val text = "E' stato addebitato il pagamento di una domiciliazione di 20,00 € da parte di MSF"
        assertEquals(2_000L, Money.findAmount(text))
        assertEquals(123_450L, Money.findAmount("Pagamento di EUR 1.234,50 presso ESSELUNGA"))
        assertEquals(990L, Money.findAmount("Hai pagato 9,90€ da Bar Sport"))
        assertNull(Money.findAmount("Nessun importo qui"))
    }
}
