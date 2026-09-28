package com.davidegigante.spesesmart.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Mese come intero yyyyMM (es. 202610): comodo da salvare nel database e da confrontare. */
fun YearMonth.toKey(): Int = year * 100 + monthValue

fun monthFromKey(key: Int): YearMonth = YearMonth.of(key / 100, key % 100)

/** La settimana inizia di lunedì. */
fun LocalDate.startOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun LocalDate.startMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Giorno di scadenza reale nel mese: "il 31" a febbraio diventa il 28 (o 29). */
fun YearMonth.dueDate(dueDay: Int): LocalDate = atDay(dueDay.coerceIn(1, lengthOfMonth()))
