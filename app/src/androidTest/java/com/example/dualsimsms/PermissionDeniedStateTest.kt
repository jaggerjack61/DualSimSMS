package com.example.dualsimsms

import android.os.SystemClock
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dualsimsms.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Without runtime permissions the app must degrade gracefully: the folder
 * views show a permission-required state instead of crashing or showing
 * stale data.
 */
@RunWith(AndroidJUnit4::class)
class PermissionDeniedStateTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun permissionDeniedShowsErrorState() {
        waitUntil(8000) { errorViewVisible() }
        onView(withId(R.id.errorTitle)).check(matches(isDisplayed()))
        onView(withId(R.id.errorAction)).check(matches(isDisplayed()))
    }

    private fun errorViewVisible(): Boolean {
        var visible = false
        activityRule.scenario.onActivity { activity ->
            val error = activity.findViewById<android.view.View>(R.id.errorView)
            visible = error != null && error.visibility == android.view.View.VISIBLE
        }
        return visible
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Condition not met within $timeoutMs ms")
    }
}
