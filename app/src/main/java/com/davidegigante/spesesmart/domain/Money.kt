package com.davidegigante.spesesmart.domain

import java.math.BigDecimal
import kotlin.math.abs

/** Importi in centesimi (Long), formattati all'italiana: "1.200,50 €". */
object Money {

    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        val euros = (absolute / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        val decimals = (absolute % 100).toString().padStart(2, '0')
        return "$sign$euros,$decimals €"
    }

    /** Valore da mettere in un campo di testo modificabile: "12,50" (senza separatore delle migliaia). */
    fun toInput(cents: Long): String = "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

    /**
     * Legge un importo scritto a mano: "12", "12,5", "12,50", "12.50", "1.200,50", "€ 3,20".
     * Restituisce null se il testo non è un importo valido.
     */
    fun parse(input: String): Long? {
        var s = input.replace("€", "").replace(" ", "").replace(" ", "").trim()
        if (s.isEmpty()) return null
        val hasComma = ',' in s
        val hasDot = '.' in s
        s = when {
            hasComma && hasDot -> s.replace(".", "").replace(',', '.')
            hasComma -> s.replace(',', '.')
            // Solo punti: "12.50" è un decimale, "1.200" sono le migliaia.
            hasDot && s.substringAfterLast('.').length == 3 -> s.replace(".", "")
            else -> s
        }
        if (!Regex("""\d+(\.\d{1,2})?""").matches(s)) return null
        return BigDecimal(s).movePointRight(2).longValueExact()
    }

    private val AMOUNT_BEFORE_EURO = Regex("""(\d{1,3}(?:\.\d{3})+|\d+),(\d{2})\s?(?:€|EUR|euro)""", RegexOption.IGNORE_CASE)
    private val AMOUNT_AFTER_EURO = Regex("""(?:€|EUR)\s?(\d{1,3}(?:\.\d{3})+|\d+),(\d{2})""", RegexOption.IGNORE_CASE)

    /**
     * Cerca il primo importo in euro dentro un testo libero (es. una notifica).
     * È solo un ripiego generico: i parser veri, per app, arriveranno con i testi reali di Isybank.
     */
    fun findAmount(text: String): Long? {
        val match = AMOUNT_BEFORE_EURO.find(text) ?: AMOUNT_AFTER_EURO.find(text) ?: return null
        val euros = match.groupValues[1].replace(".", "").toLong()
        return euros * 100 + match.groupValues[2].toLong()
    }
}
