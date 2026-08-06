package com.example.dualsimsms

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dualsimsms.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end persistence of custom SIM settings through Preferences DataStore.
 * Uses dedicated subscription ids so runs do not interfere with each other.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDataStoreTest {

    private val testSubId = 90001

    private fun repository(): SettingsRepository =
        SettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun customNameAndColorPersistAndReset() = runBlocking {
        val repo = repository()
        repo.setSimName(testSubId, "Work")
        repo.setSimColor(testSubId, 0xFF112233.toInt())
        repo.setLastSim(testSubId)

        val settings = repo.settingsFlow().first()
        assertEquals("Work", settings.sims[testSubId]?.customName)
        assertEquals(0xFF112233.toInt(), settings.sims[testSubId]?.colorArgb)
        assertEquals(testSubId, settings.lastSimSubId)

        // Reset to default by clearing the custom name.
        repo.setSimName(testSubId, null)
        val afterReset = repo.settingsFlow().first()
        assertNull(afterReset.sims[testSubId]?.customName)
        assertEquals(0xFF112233.toInt(), afterReset.sims[testSubId]?.colorArgb)

        // Blank names are normalized away.
        repo.setSimColor(testSubId, 0)
    }

    @Test
    fun lastUsedSimIsTracked() = runBlocking {
        val repo = repository()
        repo.setLastSim(testSubId + 1)
        val lastSim: Int? = repo.settingsFlow().first().lastSimSubId
        assertEquals(testSubId + 1, lastSim)
    }

    @Test
    fun blankNamesAreNormalizedAway() = runBlocking {
        val repo = repository()
        repo.setSimName(testSubId, "   ")
        val settings = repo.settingsFlow().first()
        assertNull(settings.sims[testSubId]?.customName)
    }
}
