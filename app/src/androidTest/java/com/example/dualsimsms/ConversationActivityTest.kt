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
import com.example.dualsimsms.ui.ConversationActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Conversation screen: bubbles area, SIM selector, draft input and the
 * send failure state without the SMS role.
 */
@RunWith(AndroidJUnit4::class)
class ConversationActivityTest {

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
    fun composeBarCountsAndSendBlockedWithoutRole() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = ConversationActivity.intent(context, -1L, "+15550001111", null)

        ActivityScenario.launch<ConversationActivity>(intent).use { scenario ->
            onView(withId(R.id.bodyInput)).check(matches(isDisplayed()))
            onView(withId(R.id.simSpinner)).check(matches(isDisplayed()))

            onView(withId(R.id.bodyInput)).perform(replaceText("Test draft"))
            waitUntil(4000) { countLabel(scenario) == "10 chars · 1 SMS" }

            // Without the default SMS role, sending is rejected with a clear message.
            onView(withId(R.id.sendButton)).perform(click())
            waitUntil(4000) {
                onView(withText(R.string.error_not_default_handler))
                    .check(matches(isDisplayed()))
                true
            }
        }
    }

    private fun countLabel(scenario: ActivityScenario<ConversationActivity>): String {
        var text = ""
        scenario.onActivity { activity ->
            text = activity.findViewById<android.widget.TextView>(R.id.countLabel).text.toString()
        }
        return text
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
