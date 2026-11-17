package com.irontrack.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatWeight(value: Double): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

fun formatDate(value: LocalDate?): String =
    value?.format(DateTimeFormatter.ofPattern("dd.MM.yy")) ?: "—"

fun formatDateLong(value: LocalDate?): String =
    value?.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))) ?: "—"
