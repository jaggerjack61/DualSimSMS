package com.example.dualsimsms.util

import android.provider.Telephony
import com.example.dualsimsms.model.Message
import com.example.dualsimsms.model.MessageDeliveryState

/**
 * Some telephony providers create their own sent row in addition to the row
 * maintained by the default SMS app. Collapse exact provider copies while
 * retaining the row with the most useful delivery state.
 */
object MessageDeduplicator {

    fun deduplicate(messages: List<Message>): List<Message> =
        messages.groupBy(::key).values.map { copies ->
            copies.maxWith(
                compareBy<Message>(::stateRank)
                    .thenBy(Message::date)
                    .thenBy(Message::id)
            )
        }

    private fun key(message: Message): MessageKey {
        val isDraft = message.type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT
        return MessageKey(
            conversation = if (message.threadId > 0) {
                "thread:${message.threadId}"
            } else {
                "address:${canonicalAddress(message.address)}"
            },
            // There should only be one current draft per conversation. Ignore
            // body, date and SIM so stale versions collapse to the newest row.
            body = if (isDraft) "" else message.body,
            date = if (isDraft) 0L else message.date,
            subId = if (isDraft) null else message.subId,
            // Direction/type is intentionally ignored for exact timestamped
            // copies. Corrupted provider rows can surface once as Inbox and
            // again as Failed/Sent even though they are the same physical SMS.
            kind = if (isDraft) DRAFT_KIND else MESSAGE_KIND
        )
    }

    private fun canonicalAddress(address: String): String {
        val trimmed = address.trim()
        val isDialable = trimmed.any(Char::isDigit) && trimmed.all { character ->
            character.isDigit() || character in "+-() ."
        }
        return if (isDialable) {
            val digits = trimmed.filter(Char::isDigit)
            if (trimmed.startsWith('+')) "+$digits" else digits
        } else {
            trimmed.lowercase()
        }
    }

    private fun stateRank(message: Message): Int = when (message.deliveryState) {
        MessageDeliveryState.DELIVERED -> 5
        MessageDeliveryState.SENT -> 4
        // Prefer a real inbox row over an exact failed/outbox copy.
        MessageDeliveryState.NONE -> if (message.isIncoming) 3 else 0
        MessageDeliveryState.FAILED -> 2
        MessageDeliveryState.SENDING -> 1
    }

    private data class MessageKey(
        val conversation: String,
        val body: String,
        val date: Long,
        val subId: Int?,
        val kind: Int
    )

    private const val MESSAGE_KIND = -100
    private const val DRAFT_KIND = -101
}
