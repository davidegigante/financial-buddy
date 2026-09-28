package com.davidegigante.spesesmart.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ITALIAN: Locale = Locale.ITALY

private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd/MM/yyyy HH:mm:ss", ITALIAN)
private val timeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", ITALIAN)
private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", ITALIAN)
private val shortDayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", ITALIAN)
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", ITALIAN)
private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", ITALIAN)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", ITALIAN)

fun formatDateTime(epochMillis: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun formatShortDateTime(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** "Lunedì 28 settembre" */
fun formatDayHeader(date: LocalDate): String = dayHeaderFormatter.format(date).capitalized()

/** "lun 28 set" */
fun formatShortDay(date: LocalDate): String = shortDayFormatter.format(date)

/** "28 settembre" */
fun formatDayMonth(date: LocalDate): String = dayMonthFormatter.format(date)

/** "Lunedì 28 settembre 2026" */
fun formatFullDate(date: LocalDate): String = fullDateFormatter.format(date).capitalized()

/** "Settembre 2026" */
fun formatMonth(month: YearMonth): String = monthFormatter.format(month).capitalized()

private fun String.capitalized(): String = replaceFirstChar { it.titlecase(ITALIAN) }
