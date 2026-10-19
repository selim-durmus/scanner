package com.tuttoposto.scanner.ui.common

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Format {
    private val zone get() = ZoneId.systemDefault()

    fun pages(n: Int): String = when {
        n <= 0 -> "…"
        n == 1 -> "1 page"
        else -> "$n pages"
    }

    fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    /** "14:02", "Yesterday", "Mon", "Jun 24", "Jun 24, 2025": the shortest that's unambiguous. */
    fun shortDate(context: Context, millis: Long, today: LocalDate = LocalDate.now()): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        val d = t.toLocalDate()
        val days = ChronoUnit.DAYS.between(d, today)
        val locale = Locale.getDefault()
        return when {
            days == 0L -> t.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale))
            days == 1L -> "Yesterday"
            days in 2..6 -> t.format(DateTimeFormatter.ofPattern("EEE", locale))
            d.year == today.year -> t.format(DateTimeFormatter.ofPattern("MMM d", locale))
            else -> t.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
        }
    }

    fun longDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy · HH:mm", Locale.getDefault()))

    /** Library section for a date when sorted by most recent. */
    fun bucket(millis: Long, today: LocalDate = LocalDate.now()): String {
        val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(d, today)
        return when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> "This week"
            d.year == today.year && d.month == today.month -> "Earlier this month"
            d.year == today.year -> d.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
            else -> d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }
    }
}
