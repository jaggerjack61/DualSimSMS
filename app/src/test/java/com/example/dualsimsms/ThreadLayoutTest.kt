package com.example.dualsimsms

import com.example.dualsimsms.model.Message
import com.example.dualsimsms.util.ThreadLayout
import com.example.dualsimsms.util.ThreadLayout.Bubble
import com.example.dualsimsms.util.ThreadLayout.DayHeader
import com.example.dualsimsms.util.TimeFormatter
import com.example.dualsimsms.util.TimeFormatter.DayBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ThreadLayoutTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun msg(id: Long, date: Long, incoming: Boolean = true, subId: Int? = 1, type: Int? = null) =
        Message(
            id = id,
            threadId = 1,
            address = "+15550001111",
            body = "m$id",
            date = date,
            read = true,
            subId = subId,
            type = type ?: if (incoming) 1 else 2
        )

    @Test
    fun emptyThreadHasNoRows() {
        assertTrue(ThreadLayout.build(emptyList(), zone).isEmpty())
    }

    @Test
    fun insertsHeaderBeforeFirstMessageOfEachDay() {
        val rows = ThreadLayout.build(
            listOf(msg(1, at(20, 9)), msg(2, at(20, 18)), msg(3, at(21, 8))),
            zone
        )
        assertEquals(5, rows.size)
        assertTrue(rows[0] is DayHeader)
        assertTrue(rows[1] is Bubble)
        assertTrue(rows[2] is Bubble)
        assertTrue(rows[3] is DayHeader)
        assertEquals(at(21, 8), (rows[3] as DayHeader).timestamp)
    }

    @Test
    fun groupsQuickSameDirectionSameSimMessages() {
        val bubbles = ThreadLayout.build(
            listOf(msg(1, at(20, 9, 0)), msg(2, at(20, 9, 2)), msg(3, at(20, 9, 4))),
            zone
        ).filterIsInstance<Bubble>()
        assertFalse(bubbles[0].joinsPrevious)
        assertTrue(bubbles[0].joinsNext)
        assertTrue(bubbles[1].joinsPrevious && bubbles[1].joinsNext)
        assertTrue(bubbles[2].joinsPrevious)
        assertFalse(bubbles[2].joinsNext)
        // Only the last bubble of the group carries the footer.
        assertEquals(listOf(false, false, true), bubbles.map { it.showMeta })
    }

    @Test
    fun directionSimAndTimeGapBreakGroups() {
        val bubbles = ThreadLayout.build(
            listOf(
                msg(1, at(20, 9, 0)),
                msg(2, at(20, 9, 1), incoming = false),
                msg(3, at(20, 9, 2), incoming = false, subId = 2),
                msg(4, at(20, 9, 30), incoming = false, subId = 2)
            ),
            zone
        ).filterIsInstance<Bubble>()
        assertTrue(bubbles.none { it.joinsPrevious || it.joinsNext })
        assertTrue(bubbles.all { it.showMeta })
    }

    @Test
    fun failedMessageAlwaysShowsFooterAndEndsGroup() {
        val failed = 5 // MESSAGE_TYPE_FAILED
        val bubbles = ThreadLayout.build(
            listOf(
                msg(1, at(20, 9, 0), incoming = false),
                msg(2, at(20, 9, 1), incoming = false, type = failed),
                msg(3, at(20, 9, 2), incoming = false)
            ),
            zone
        ).filterIsInstance<Bubble>()
        assertTrue(bubbles[1].showMeta)
        assertFalse(bubbles[1].joinsNext)
    }

    @Test
    fun dayBuckets() {
        val now = at(26, 12)
        assertEquals(DayBucket.TODAY, TimeFormatter.bucket(at(26, 0, 1), now, zone))
        assertEquals(DayBucket.YESTERDAY, TimeFormatter.bucket(at(25, 23, 59), now, zone))
        assertEquals(DayBucket.THIS_WEEK, TimeFormatter.bucket(at(20, 12), now, zone))
        assertEquals(DayBucket.THIS_YEAR, TimeFormatter.bucket(at(19, 12), now, zone))
        val lastYear = LocalDateTime.of(2025, 12, 31, 12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(DayBucket.OLDER, TimeFormatter.bucket(lastYear, now, zone))
        // Clock skew: a slightly-future timestamp still reads as today.
        assertEquals(DayBucket.TODAY, TimeFormatter.bucket(now + 60_000, now, zone))
    }
}
