package com.example.dualsimsms

import android.Manifest
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.dualsimsms.ui.ComposeActivity
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen behavior: prefilled recipient, character and segment
 * counts, and the failure state when the SMS role is not held.
 */
@RunWith(AndroidJUnit4::class)
class ComposeActivityTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    @Test
    fun recipientPrefillCountsAndSendFailureState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = ComposeActivity.intent(context, "+15551234567")

        ActivityScenario.launch<ComposeActivity>(intent).use { scenario ->
            onView(withId(R.id.recipientInput)).check(matches(withText("+15551234567")))

            onView(withId(R.id.bodyInput)).perform(replaceText("Hello SIM"))
            waitUntil(4000) { countLabel(scenario) == "12 chars · 1 messages" }
            assertEquals("12 chars · 1 messages", countLabel(scenario))

            // A long body is split into multiple SMS segments.
            onView(withId(R.id.bodyInput)).perform(
                replaceText("A".repeat(200))
            )
            waitUntil(4000) { countLabel(scenario) == "200 chars · 2 messages" }

            // Sending without the default SMS role must show a clear error.
            waitUntil(8000) { sendEnabled(scenario) }
            onView(withId(R.id.sendButton)).perform(click())
            waitUntil(4000) {
                onView(withText(R.string.error_not_default_handler))
                    .check(matches(isDisplayed()))
                true
            }
        }
    }

    private fun countLabel(scenario: ActivityScenario<ComposeActivity>): String {
        var text = ""
        scenario.onActivity { activity ->
            text = activity.findViewById<android.widget.TextView>(R.id.countLabel).text.toString()
        }
        return text
    }

    private fun sendEnabled(scenario: ActivityScenario<ComposeActivity>): Boolean {
        var enabled = false
        scenario.onActivity { activity ->
            enabled = activity.findViewById<MaterialButton>(R.id.sendButton).isEnabled
        }
        return enabled
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
