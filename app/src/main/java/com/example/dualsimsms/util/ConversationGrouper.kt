package com.example.dualsimsms.util

import com.example.dualsimsms.model.Conversation
import com.example.dualsimsms.model.Message

/**
 * Groups individual messages into conversation threads, sorted most-recent first.
 *
 * Messages whose thread id is unknown (0 or negative) are grouped by their
 * address so they still form stable conversation rows.
 */
object ConversationGrouper {

    fun group(messages: List<Message>): List<Conversation> {
        val grouped = messages.groupBy { keyOf(it) }
        return grouped.map { (_, msgs) ->
            val sorted = msgs.sortedByDescending { it.date }
            val latest = sorted.first()
            Conversation(
                threadId = if (latest.threadId > 0) latest.threadId else 0,
                address = latest.address,
                snippet = latest.body,
                date = latest.date,
                unreadCount = sorted.count { !it.read },
                subId = latest.subId,
                messageCount = sorted.size
            )
        }.sortedByDescending { it.date }
    }

    private fun keyOf(message: Message): String =
        if (message.threadId > 0) "thread:${message.threadId}" else "address:${message.address}"
}
