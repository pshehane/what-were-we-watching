package net.shehane.watching.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * One place for time. Everything stored is an ISO-8601 instant in UTC so the JSON
 * sorts and compares as plain text, and the CSV gets plain YYYY-MM-DD dates that
 * Sheets and Excel both read as dates without an import dialog.
 */
object Clock {

    fun now(): String = Instant.now().toString()

    fun plusDays(days: Long): String = Instant.now().plus(days, ChronoUnit.DAYS).toString()

    fun parseOrNull(iso: String?): Instant? =
        if (iso.isNullOrBlank()) null else runCatching { Instant.parse(iso) }.getOrNull()

    /** True when [iso] is in the past. A missing or unparseable value counts as past. */
    fun isPast(iso: String?): Boolean {
        val t = parseOrNull(iso) ?: return true
        return t.isBefore(Instant.now())
    }

    /** "2026-09-11" for the CSV. Empty for a missing value. */
    fun toCsvDate(iso: String?): String {
        val t = parseOrNull(iso) ?: return ""
        return LocalDate.ofInstant(t, ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /** Whole days from [iso] until now. Negative when [iso] is in the future. */
    fun daysSince(iso: String?): Long? {
        val t = parseOrNull(iso) ?: return null
        return ChronoUnit.DAYS.between(
            LocalDate.ofInstant(t, ZoneId.systemDefault()),
            LocalDate.now(ZoneId.systemDefault()),
        )
    }

    /** Whole days from now until [iso]. Negative when [iso] has passed. */
    fun daysUntil(iso: String?): Long? = daysSince(iso)?.let { -it }

    /** "yesterday", "6 days ago", "3 weeks ago", "2 months ago". */
    fun ago(iso: String?): String {
        val d = daysSince(iso) ?: return "not watched yet"
        return when {
            d <= 0L -> "today"
            d == 1L -> "yesterday"
            d < 14L -> "$d days ago"
            d < 60L -> "${Math.round(d / 7.0)} weeks ago"
            d < 365L -> "${Math.round(d / 30.0)} months ago"
            else -> "over a year ago"
        }
    }

    /** "back tomorrow", "back in 11 days". */
    fun until(iso: String?): String {
        val d = daysUntil(iso) ?: return "back soon"
        return when {
            d <= 0L -> "back now"
            d == 1L -> "back tomorrow"
            else -> "back in $d days"
        }
    }

    /** Later of two ISO instants, treating null and unparseable as older. */
    fun newer(a: String?, b: String?): Boolean {
        val ta = parseOrNull(a) ?: return false
        val tb = parseOrNull(b) ?: return true
        return ta.isAfter(tb)
    }
}
