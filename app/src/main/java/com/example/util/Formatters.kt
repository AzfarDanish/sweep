package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.pow

object Formatters {
    private val dayHeaderFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val monthNameFormat = SimpleDateFormat("MMMM", Locale.getDefault())

    fun formatDateGroupLabel(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return "Unknown date"
        val target = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(target, today) -> "Today"
            isSameDay(target, yesterday) -> "Yesterday"
            else -> dayHeaderFormat.format(Date(timestampMillis))
        }
    }

    fun formatDateKey(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return "0000-00-00"
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    fun yearMonthPair(timestampMillis: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (timestampMillis > 0L) timestampMillis else System.currentTimeMillis()
        }
        return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }

    fun monthDisplayName(monthOneBased: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, (monthOneBased - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return monthNameFormat.format(cal.time)
    }

    fun formatFullDateTime(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return "Unknown"
        return fullDateFormat.format(Date(timestampMillis))
    }

    fun formatShortDate(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return "Unknown"
        return shortDateFormat.format(Date(timestampMillis))
    }

    fun formatDuration(durationMillis: Long): String {
        if (durationMillis <= 0L) return "0:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups == 0) {
            "$bytes B"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
