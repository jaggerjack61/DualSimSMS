package com.example.dualsimsms

import com.example.dualsimsms.util.DraftLogic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftLogicTest {

    @Test
    fun blankBodiesAreNotSaved() {
        assertFalse(DraftLogic.shouldSave("", null))
        assertFalse(DraftLogic.shouldSave("   ", null))
    }

    @Test
    fun newNonBlankBodyIsSaved() {
        assertTrue(DraftLogic.shouldSave("hello", null))
        assertTrue(DraftLogic.shouldSave("hello", ""))
    }

    @Test
    fun unchangedBodyIsNotResaved() {
        assertFalse(DraftLogic.shouldSave("hello", "hello"))
    }

    @Test
    fun changedBodyIsResaved() {
        assertTrue(DraftLogic.shouldSave("hello there", "hello"))
    }

    @Test
    fun clearedBodyDeletesDraft() {
        assertTrue(DraftLogic.shouldDelete(""))
        assertTrue(DraftLogic.shouldDelete("  "))
        assertFalse(DraftLogic.shouldDelete("hello"))
    }

    @Test
    fun debounceWindowIsPositive() {
        assertTrue(DraftLogic.DEBOUNCE_MILLIS > 0)
    }
}
