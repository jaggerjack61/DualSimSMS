package com.example.dualsimsms.util

import android.text.format.DateUtils

object TimeFormatter {

    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String =
        DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_ALL
        ).toString()
}
