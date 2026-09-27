package com.example.dualsimsms

import com.example.dualsimsms.model.Message
import com.example.dualsimsms.util.ConversationGrouper
import com.example.dualsimsms.util.ConversationSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationSearchTest {

    private fun message(id: Long, threadId: Long, address: String, body: String, date: Long) =
        Message(id, threadId, address, body, date, true, null, 1)

    private val messages = listOf(
        message(1, 10, "+263771234567", "Lunch at noon?", 100),
        message(2, 10, "+263771234567", "See you there", 200),
        message(3, 11, "+263789999999", "Your code is 4411", 150),
        message(4, 12, "0", "Balance low", 50)
    )

    private val conversations = ConversationGrouper.group(messages).map {
        if (it.threadId == 10L) it.copy(contactName = "Alice") else it
    }

    private fun search(query: String) = ConversationSearch.filter(conversations, messages, query)

    @Test
    fun blankQueryKeepsEveryConversation() {
        assertEquals(conversations, search("  "))
    }

    @Test
    fun matchesContactNameIgnoringCase() {
        val results = search("alice")
        assertEquals(listOf(10L), results.map { it.threadId })
        // A name match keeps the latest message as the snippet.
        assertEquals("See you there", results.single().snippet)
    }

    @Test
    fun bodyMatchShowsTheMatchingMessage() {
        val result = search("lunch").single()
        assertEquals(10L, result.threadId)
        assertEquals("Lunch at noon?", result.snippet)
        assertEquals(100L, result.date)
    }

    @Test
    fun numberMatchIgnoresFormatting() {
        assertEquals(listOf(10L), search("077 123").map { it.threadId })
        assertEquals(listOf(11L), search("+263 789").map { it.threadId })
    }

    @Test
    fun digitsInMessageBodiesMatch() {
        assertEquals(listOf(11L), search("4411").map { it.threadId })
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertEquals(emptyList<Long>(), search("zebra").map { it.threadId })
    }
}
