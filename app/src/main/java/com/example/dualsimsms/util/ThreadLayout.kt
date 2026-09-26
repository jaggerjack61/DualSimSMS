package com.example.dualsimsms.util

import com.example.dualsimsms.model.Message
import com.example.dualsimsms.model.MessageDeliveryState
import java.time.ZoneId

/**
 * Turns a date-ordered thread into display rows: a day separator before the
 * first message of each calendar day, and bubbles that know whether they
 * visually join the bubble above/below them.
 *
 * Consecutive messages group when they share a day, direction and SIM and
 * arrive within [GROUP_WINDOW_MS] of each other. A SIM change always breaks
 * the group so every group's footer shows which SIM it used.
 */
object ThreadLayout {

    const val GROUP_WINDOW_MS: Long = 5 * 60 * 1000L

    sealed interface Row

    data class DayHeader(val timestamp: Long) : Row

    data class Bubble(
        val message: Message,
        val joinsPrevious: Boolean,
        val joinsNext: Boolean
    ) : Row {
        /** Footer (SIM, time, status) is shown once per group, and always for failures. */
        val showMeta: Boolean
            get() = !joinsNext || message.deliveryState == MessageDeliveryState.FAILED
    }

    fun build(messages: List<Message>, zone: ZoneId = ZoneId.systemDefault()): List<Row> {
        val rows = ArrayList<Row>(messages.size + 4)
        messages.forEachIndexed { index, message ->
            val previous = messages.getOrNull(index - 1)
            val next = messages.getOrNull(index + 1)
            if (previous == null || !TimeFormatter.isSameDay(previous.date, message.date, zone)) {
                rows += DayHeader(message.date)
            }
            rows += Bubble(
                message = message,
                joinsPrevious = previous != null && groups(previous, message, zone),
                joinsNext = next != null && groups(message, next, zone)
            )
        }
        return rows
    }

    private fun groups(earlier: Message, later: Message, zone: ZoneId): Boolean =
        earlier.isIncoming == later.isIncoming &&
            earlier.subId == later.subId &&
            earlier.deliveryState != MessageDeliveryState.FAILED &&
            later.date - earlier.date in 0..GROUP_WINDOW_MS &&
            TimeFormatter.isSameDay(earlier.date, later.date, zone)
}
