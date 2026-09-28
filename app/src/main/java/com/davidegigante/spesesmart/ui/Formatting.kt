package com.davidegigante.spesesmart.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ITALIAN: Locale = Locale.ITALY

private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd/MM/yyyy HH:mm:ss", ITALIAN)
private val timeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", ITALIAN)

fun formatDateTime(epochMillis: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun formatShortDateTime(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
