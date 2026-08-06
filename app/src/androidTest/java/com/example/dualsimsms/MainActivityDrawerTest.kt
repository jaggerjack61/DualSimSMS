package com.example.dualsimsms

import android.Manifest
import android.os.SystemClock
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.dualsimsms.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drawer navigation and SIM tab behavior. The device is not the default SMS
 * handler during tests, so the app runs in read-only mode.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityDrawerTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

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
    fun openDrawerAndNavigateToSentDraftsAndSettings() {
        // The toolbar shows the selected folder (Inbox by default).
        waitUntil { toolbarTitle() == "Inbox" }

        // Open the drawer via the hamburger.
        onView(withContentDescriptionText("Open navigation drawer")).perform(click())
        waitUntil { drawerOpen() }
        assertTrue(drawerOpen())

        // Navigate to Sent.
        onView(withText(R.string.nav_sent)).perform(click())
        waitUntil { !drawerOpen() }
        waitUntil { toolbarTitle() == "Sent" }
        onView(withId(R.id.pager)).check(matches(isDisplayed()))

        // Navigate to Drafts.
        onView(withContentDescriptionText("Open navigation drawer")).perform(click())
        onView(withText(R.string.nav_drafts)).perform(click())
        waitUntil { toolbarTitle() == "Drafts" }

        // Navigate to Settings.
        onView(withContentDescriptionText("Open navigation drawer")).perform(click())
        onView(withText(R.string.nav_settings)).perform(click())
        waitUntil { settingsHostVisible() }
        waitUntil { toolbarTitle() == "Settings" }
        onView(withText(R.string.settings_default_sms_title)).check(matches(isDisplayed()))
        onView(withId(R.id.defaultSmsButton)).check(matches(isDisplayed()))
        onView(withText(R.string.settings_notifications_title)).check(matches(isDisplayed()))
        onView(withId(R.id.notificationButton)).check(matches(isDisplayed()))
    }

    @Test
    fun tabsRenderAndSwitchingIsStable() {
        waitUntil { tabCount() >= 1 }
        assertTrue(tabCount() >= 1)
        onView(withText(R.string.tab_all_sims)).check(matches(isDisplayed()))

        // With one SIM the second tab is "SIM 1"; with two, "SIM 2".
        // Scope the match to the TabLayout so conversation SIM badges with
        // the same label don't make it ambiguous.
        if (tabCount() >= 2) {
            onView(
                allOf(
                    withText("SIM 1"),
                    isDescendantOfA(withId(R.id.tabs))
                )
            ).perform(click())
            waitUntil { selectedTabIndex() == 1 }
        }
        onView(withId(R.id.pager)).check(matches(isDisplayed()))
    }

    private fun toolbarTitle(): String = viewProperty { activity ->
        activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            com.example.dualsimsms.R.id.toolbar
        ).title.toString()
    }

    private fun drawerOpen(): Boolean = viewProperty { activity ->
        activity.findViewById<androidx.drawerlayout.widget.DrawerLayout>(
            com.example.dualsimsms.R.id.drawerLayout
        ).isDrawerOpen(androidx.core.view.GravityCompat.START)
    }

    private fun settingsHostVisible(): Boolean = viewProperty { activity ->
        activity.findViewById<android.view.View>(
            com.example.dualsimsms.R.id.settingsHost
        ).visibility == android.view.View.VISIBLE
    }

    private fun tabCount(): Int = viewProperty { activity ->
        activity.findViewById<com.google.android.material.tabs.TabLayout>(
            com.example.dualsimsms.R.id.tabs
        ).tabCount
    }

    private fun selectedTabIndex(): Int = viewProperty { activity ->
        activity.findViewById<com.google.android.material.tabs.TabLayout>(
            com.example.dualsimsms.R.id.tabs
        ).selectedTabPosition
    }

    private fun <T> viewProperty(block: (android.app.Activity) -> T): T {
        var result: T? = null
        activityRule.scenario.onActivity { activity ->
            result = block(activity)
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(timeoutMs: Long = 8000, condition: () -> Boolean) {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Condition not met within $timeoutMs ms")
    }
}

private fun withContentDescriptionText(text: String) =
    androidx.test.espresso.matcher.ViewMatchers.withContentDescription(text)
