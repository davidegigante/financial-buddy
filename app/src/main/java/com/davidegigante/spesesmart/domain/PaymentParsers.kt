package com.davidegigante.spesesmart.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Quello che si riesce a leggere da una notifica di pagamento. */
data class ParsedPayment(
    val amountCents: Long,
    val merchant: String,
    /** null = usare l'ora di arrivo della notifica. */
    val occurredAt: LocalDateTime?,
    val note: String = "",
)

/**
 * Legge le notifiche di una app precisa. Restituisce null se il testo non è un pagamento
 * (accrediti, codici, pubblicità...): in quel caso la notifica resta solo nel registro di debug.
 */
interface PaymentParser {
    val packageName: String
    fun parse(text: String, postedAt: LocalDateTime): ParsedPayment?
}

object IsybankParser : PaymentParser {
    override val packageName = "com.intesasanpaolo.isybank.mobile"

    // "💸 Hai pagato 5,00 € con la carta *6576 il 29.09 alle ore 00:42 da BAR GUATE, SALERNO."
    private val CARD = Regex(
        """Hai pagato\s+(\d{1,3}(?:\.\d{3})*|\d+),(\d{2})\s*€\s+con la carta\s+\*?\d+\s+il\s+(\d{1,2})\.(\d{1,2})\s+alle ore\s+(\d{1,2}):(\d{2})\s+da\s+(.+?)\.?\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // "E' stato addebitato il pagamento di una domiciliazione di 20,00 € da parte di MEDICI SENZA FRONTIERE ONLUS
    //  sul conto xxx202 in data 28.09.2026"
    private val DIRECT_DEBIT = Regex(
        """domiciliazione di\s+(\d{1,3}(?:\.\d{3})*|\d+),(\d{2})\s*€\s+da parte di\s+(.+?)\s+sul conto.*?in data\s+(\d{1,2})\.(\d{1,2})\.(\d{4})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    override fun parse(text: String, postedAt: LocalDateTime): ParsedPayment? {
        CARD.find(text)?.let { m ->
            val g = m.groupValues
            val (merchant, city) = splitCity(g[7].trim())
            val day = g[3].toInt()
            val month = g[4].toInt()
            // La notifica non dice l'anno: prendiamo quello di arrivo, o il precedente a cavallo di capodanno.
            var date = runCatching { LocalDate.of(postedAt.year, month, day) }.getOrNull() ?: return null
            if (date.isAfter(postedAt.toLocalDate().plusDays(1))) date = date.minusYears(1)
            return ParsedPayment(
                amountCents = cents(g[1], g[2]),
                merchant = merchant,
                occurredAt = date.atTime(LocalTime.of(g[5].toInt(), g[6].toInt())),
                note = city,
            )
        }
        DIRECT_DEBIT.find(text)?.let { m ->
            val g = m.groupValues
            val date = runCatching { LocalDate.of(g[6].toInt(), g[5].toInt(), g[4].toInt()) }.getOrNull()
            return ParsedPayment(
                amountCents = cents(g[1], g[2]),
                merchant = prettify(g[3].trim()),
                // Solo la data: l'ora è quella della notifica.
                occurredAt = date?.atTime(postedAt.toLocalTime()),
                note = "Domiciliazione",
            )
        }
        return null
    }

    /** "BAR GUATE, SALERNO" → ("Bar Guate", "Salerno"). */
    private fun splitCity(raw: String): Pair<String, String> {
        val comma = raw.lastIndexOf(',')
        if (comma <= 0) return prettify(raw) to ""
        val city = raw.substring(comma + 1).trim()
        return if (city.isNotEmpty() && city.none { it.isDigit() }) {
            prettify(raw.substring(0, comma).trim()) to prettify(city)
        } else {
            prettify(raw) to ""
        }
    }
}

object PaymentParsers {
    val all: List<PaymentParser> = listOf(IsybankParser)
    val packages: Set<String> = all.map { it.packageName }.toSet()
    fun forPackage(pkg: String): PaymentParser? = all.find { it.packageName == pkg }
}

private fun cents(euros: String, decimals: String): Long = euros.replace(".", "").toLong() * 100 + decimals.toLong()

/** Le banche scrivono gli esercenti tutto maiuscolo: "BAR GUATE" → "Bar Guate". Il resto si lascia com'è. */
internal fun prettify(s: String): String =
    if (s.any { it.isLowerCase() }) s
    else s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
