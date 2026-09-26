package com.example.dualsimsms.util

import android.content.Context
import android.text.format.DateUtils
import com.example.dualsimsms.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object TimeFormatter {

    /** How far a timestamp is from "now", in calendar terms. */
    enum class DayBucket { TODAY, YESTERDAY, THIS_WEEK, THIS_YEAR, OLDER }

    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String =
        DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_ALL
        ).toString()

    fun bucket(
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): DayBucket {
        val day = localDate(timestamp, zone)
        val today = localDate(now, zone)
        val daysAgo = ChronoUnit.DAYS.between(day, today)
        return when {
            daysAgo <= 0L -> DayBucket.TODAY
            daysAgo == 1L -> DayBucket.YESTERDAY
            daysAgo < 7L -> DayBucket.THIS_WEEK
            day.year == today.year -> DayBucket.THIS_YEAR
            else -> DayBucket.OLDER
        }
    }

    /** True when both timestamps fall on the same local calendar day. */
    fun isSameDay(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        localDate(a, zone) == localDate(b, zone)

    /** Compact timestamp for list rows: "5:42 PM", "Yesterday", "Mon", "Sep 12", "12/09/2024". */
    fun listTimestamp(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String =
        when (bucket(timestamp, now)) {
            DayBucket.TODAY -> format(context, timestamp, DateUtils.FORMAT_SHOW_TIME)
            DayBucket.YESTERDAY -> context.getString(R.string.day_yesterday)
            DayBucket.THIS_WEEK -> format(
                context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
            )
            DayBucket.THIS_YEAR -> format(
                context, timestamp,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH
            )
            DayBucket.OLDER -> format(
                context, timestamp,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_NUMERIC_DATE
            )
        }

    /** Day separator label inside a thread: "Today", "Yesterday", "Monday", "Fri, Sep 12", "Sep 12, 2024". */
    fun dayHeader(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String =
        when (bucket(timestamp, now)) {
            DayBucket.TODAY -> context.getString(R.string.day_today)
            DayBucket.YESTERDAY -> context.getString(R.string.day_yesterday)
            DayBucket.THIS_WEEK -> format(context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY)
            DayBucket.THIS_YEAR -> format(
                context, timestamp,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH
            )
            DayBucket.OLDER -> format(
                context, timestamp,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH
            )
        }

    /** Clock time only, honouring the device's 12/24-hour setting. */
    fun clockTime(context: Context, timestamp: Long): String =
        format(context, timestamp, DateUtils.FORMAT_SHOW_TIME)

    private fun format(context: Context, timestamp: Long, flags: Int): String =
        DateUtils.formatDateTime(context, timestamp, flags)

    private fun localDate(timestamp: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}
