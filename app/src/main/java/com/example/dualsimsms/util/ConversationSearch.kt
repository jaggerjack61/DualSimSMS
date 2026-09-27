package com.example.dualsimsms.util

import com.example.dualsimsms.model.Conversation
import com.example.dualsimsms.model.Message

/**
 * Filters conversation rows by a search query. A row matches on its contact
 * name, its address, or the body of any of its messages. When only a message
 * body matches, the row shows the most recent matching message instead of the
 * latest one, so the reason it matched is visible.
 */
object ConversationSearch {

    private const val MIN_NUMBER_DIGITS = 3

    fun filter(
        conversations: List<Conversation>,
        messages: List<Message>,
        query: String
    ): List<Conversation> {
        val needle = query.trim()
        if (needle.isEmpty()) return conversations

        val latestMatch = HashMap<String, Message>()
        messages.forEach { message ->
            if (message.body.contains(needle, ignoreCase = true)) {
                val key = ConversationGrouper.keyOf(message)
                val current = latestMatch[key]
                if (current == null || message.date > current.date) latestMatch[key] = message
            }
        }

        return conversations.mapNotNull { conversation ->
            when {
                matchesContact(conversation, needle) -> conversation
                else -> latestMatch[ConversationGrouper.keyOf(conversation)]?.let { match ->
                    conversation.copy(snippet = match.body, date = match.date)
                }
            }
        }.sortedByDescending { it.date }
    }

    private fun matchesContact(conversation: Conversation, needle: String): Boolean {
        if (conversation.contactName?.contains(needle, ignoreCase = true) == true) return true
        if (conversation.address.contains(needle, ignoreCase = true)) return true
        // "077 123" should find "+26377123…": compare digits only for
        // number-like queries, dropping a local trunk prefix of 0.
        val looksLikeNumber = needle.all { it.isDigit() || it in "+-() " }
        val digits = needle.filter(Char::isDigit).trimStart('0')
        return looksLikeNumber && digits.length >= MIN_NUMBER_DIGITS &&
            conversation.address.filter(Char::isDigit).contains(digits)
    }
}
