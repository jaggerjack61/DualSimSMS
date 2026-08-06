package com.example.dualsimsms

import com.example.dualsimsms.model.Message
import com.example.dualsimsms.util.ConversationGrouper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationGrouperTest {

    private fun message(
        id: Long,
        threadId: Long,
        address: String,
        body: String,
        date: Long,
        read: Boolean = true,
        subId: Int? = null
    ) = Message(id, threadId, address, body, date, read, subId, 1)

    @Test
    fun groupsByThreadId() {
        val messages = listOf(
            message(1, 10, "+111", "hello", 100),
            message(2, 10, "+111", "hello again", 200),
            message(3, 11, "+222", "other", 150)
        )
        val conversations = ConversationGrouper.group(messages)
        assertEquals(2, conversations.size)
        // Thread 10's latest message (200) sorts it first.
        assertEquals(10, conversations.first().threadId)
        assertEquals(2, conversations.first().messageCount)
        assertEquals("hello again", conversations.first().snippet)
        assertEquals(11, conversations[1].threadId)
    }

    @Test
    fun sortsConversationsByMostRecent() {
        val messages = listOf(
            message(1, 10, "+111", "old", 100),
            message(2, 11, "+222", "newer", 500)
        )
        val conversations = ConversationGrouper.group(messages)
        assertEquals(11, conversations.first().threadId)
        assertEquals(500, conversations.first().date)
    }

    @Test
    fun countsUnreadMessages() {
        val messages = listOf(
            message(1, 10, "+111", "a", 100, read = false),
            message(2, 10, "+111", "b", 200, read = false),
            message(3, 10, "+111", "c", 300, read = true)
        )
        val conversations = ConversationGrouper.group(messages)
        assertEquals(2, conversations.single().unreadCount)
    }

    @Test
    fun usesLatestMessageSubIdForBadge() {
        val messages = listOf(
            message(1, 10, "+111", "from sim1", 100, subId = 1),
            message(2, 10, "+111", "from sim2", 200, subId = 2)
        )
        val conversations = ConversationGrouper.group(messages)
        assertEquals(2, conversations.single().subId)
    }

    @Test
    fun groupsMessagesWithoutThreadByAddress() {
        val messages = listOf(
            message(1, 0, "+111", "no thread", 100, subId = 1),
            message(2, 0, "+222", "other", 150, subId = 2)
        )
        val conversations = ConversationGrouper.group(messages)
        assertEquals(2, conversations.size)
        // Sorted by most recent message first: +222 (150) precedes +111 (100).
        assertEquals("+222", conversations.first().address)
        assertEquals(0, conversations.first().threadId)
        assertEquals(listOf("+222", "+111"), conversations.map { it.address })
    }

    @Test
    fun emptyInputProducesEmptyList() {
        assertTrue(ConversationGrouper.group(emptyList()).isEmpty())
    }
}
