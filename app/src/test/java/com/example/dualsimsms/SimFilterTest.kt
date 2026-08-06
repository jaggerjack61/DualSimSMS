package com.example.dualsimsms

import com.example.dualsimsms.model.Message
import com.example.dualsimsms.util.SimFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class SimFilterTest {

    private fun message(id: Long, subId: Int?) =
        Message(id, 1, "+111", "body", 100, true, subId, 1)

    @Test
    fun nullFilterKeepsEverything() {
        val messages = listOf(message(1, 1), message(2, 2), message(3, null))
        assertEquals(3, SimFilter.filter(messages, null).size)
    }

    @Test
    fun filtersToMatchingSubscription() {
        val messages = listOf(message(1, 1), message(2, 2), message(3, 1))
        val filtered = SimFilter.filter(messages, 1)
        assertEquals(listOf(1L, 3L), filtered.map { it.id })
    }

    @Test
    fun unknownSubIdsOnlyMatchAllView() {
        val messages = listOf(message(1, null), message(2, null))
        assertEquals(0, SimFilter.filter(messages, 1).size)
        assertEquals(2, SimFilter.filter(messages, null).size)
    }

    @Test
    fun emptyInputIsSafe() {
        assertEquals(0, SimFilter.filter(emptyList(), 1).size)
        assertEquals(0, SimFilter.filter(emptyList(), null).size)
    }
}
