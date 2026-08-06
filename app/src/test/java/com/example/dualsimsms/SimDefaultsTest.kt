package com.example.dualsimsms

import com.example.dualsimsms.util.SimDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimDefaultsTest {

    @Test
    fun defaultColorsFollowSlots() {
        assertEquals(SimDefaults.COLOR_SIM_1, SimDefaults.defaultColor(0))
        assertEquals(SimDefaults.COLOR_SIM_2, SimDefaults.defaultColor(1))
    }

    @Test
    fun unknownSlotsGetNeutralColor() {
        assertEquals(SimDefaults.COLOR_UNKNOWN, SimDefaults.defaultColor(5))
        assertEquals(SimDefaults.COLOR_UNKNOWN, SimDefaults.defaultColor(-1))
    }

    @Test
    fun defaultNamesFollowSlots() {
        assertEquals("SIM 1", SimDefaults.defaultName(0))
        assertEquals("SIM 2", SimDefaults.defaultName(1))
        assertEquals("SIM 3", SimDefaults.defaultName(2))
    }

    @Test
    fun simOneAndSimTwoColorsDiffer() {
        assertNotEquals(SimDefaults.COLOR_SIM_1, SimDefaults.COLOR_SIM_2)
    }

    @Test
    fun paletteIsAccessibleAndComplete() {
        assertTrueSize(SimDefaults.PALETTE)
        assertEquals(SimDefaults.PALETTE.distinct(), SimDefaults.PALETTE)
        assertEquals(SimDefaults.COLOR_SIM_1, SimDefaults.PALETTE.first())
        assertEquals(SimDefaults.COLOR_SIM_2, SimDefaults.PALETTE[1])
    }

    @Test
    fun namesAreNormalized() {
        assertNull(SimDefaults.normalizeName(null))
        assertNull(SimDefaults.normalizeName(""))
        assertNull(SimDefaults.normalizeName("   "))
        assertEquals("Work", SimDefaults.normalizeName("  Work  "))
        assertEquals("Work SIM", SimDefaults.normalizeName("  Work SIM  "))
        assertEquals("SIM", SimDefaults.normalizeName("SIM"))
    }

    private fun assertTrueSize(list: List<*>): Int = list.size.also {
        assertEquals(8, it)
    }
}
