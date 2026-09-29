package com.davidegigante.spesesmart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class IsybankParserTest {

    private val posted = LocalDateTime.of(2026, 9, 29, 0, 49)

    @Test
    fun `pagamento con carta - testo reale`() {
        val p = IsybankParser.parse(
            "💸 Hai pagato 5,00 € con la carta *6576 il 29.09 alle ore 00:42 da BAR GUATE, SALERNO.",
            posted,
        )!!
        assertEquals(500L, p.amountCents)
        assertEquals("Bar Guate", p.merchant)
        assertEquals("Salerno", p.note)
        assertEquals(LocalDateTime.of(2026, 9, 29, 0, 42), p.occurredAt)
    }

    @Test
    fun `pagamento con carta - importo con migliaia ed esercente senza citta`() {
        val p = IsybankParser.parse("Hai pagato 1.234,56 € con la carta *1111 il 28.09 alle ore 18:05 da AMAZON MKTPL", posted)!!
        assertEquals(123_456L, p.amountCents)
        assertEquals("Amazon Mktpl", p.merchant)
        assertEquals("", p.note)
    }

    @Test
    fun `a cavallo di capodanno prende l'anno prima`() {
        val p = IsybankParser.parse(
            "Hai pagato 3,00 € con la carta *6576 il 31.12 alle ore 23:59 da BAR, ROMA.",
            LocalDateTime.of(2027, 1, 1, 0, 5),
        )!!
        assertEquals(LocalDateTime.of(2026, 12, 31, 23, 59), p.occurredAt)
    }

    @Test
    fun domiciliazione() {
        val p = IsybankParser.parse(
            "E' stato addebitato il pagamento di una domiciliazione di 20,00 € da parte di " +
                "MEDICI SENZA FRONTIERE ONLUS sul conto xxx202 in data 28.09.2026",
            posted,
        )!!
        assertEquals(2_000L, p.amountCents)
        assertEquals("Medici Senza Frontiere Onlus", p.merchant)
        assertEquals(LocalDateTime.of(2026, 9, 28, 0, 49), p.occurredAt)
    }

    @Test
    fun `testi che non sono pagamenti vengono ignorati`() {
        assertNull(IsybankParser.parse("Il tuo codice di accesso è 123456", posted))
        assertNull(IsybankParser.parse("Hai ricevuto un bonifico di 50,00 € da MARIO ROSSI", posted))
    }
}
