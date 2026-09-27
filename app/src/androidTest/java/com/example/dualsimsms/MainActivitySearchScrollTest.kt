package com.example.dualsimsms

import android.Manifest
import android.os.SystemClock
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.viewpager2.widget.ViewPager2
import com.example.dualsimsms.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search and scroll-to-top on the conversation list. Read-only: uses
 * whatever conversations are already on the device.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySearchScrollTest {

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
    fun searchWithNoMatchShowsNoResultsAndClosingRestoresList() {
        waitUntil { itemCount() > 0 }
        val before = itemCount()

        onActivity { searchItem(it).expandActionView() }
        onActivity { searchView(it).setQuery("zqxj-no-such-message", false) }
        waitUntil { viewProperty { pageOf(it)?.findViewById<View>(R.id.emptyView)?.isShown == true } }
        onActivity { activity ->
            val title = pageOf(activity)!!.findViewById<android.widget.TextView>(R.id.emptyTitle)
            assertEquals(activity.getString(R.string.empty_search_title), title.text.toString())
        }

        onActivity { searchItem(it).collapseActionView() }
        waitUntil { viewProperty { recyclerOf(it)?.isShown == true } && itemCount() == before }
    }

    @Test
    fun searchNarrowsToMatchingConversation() {
        waitUntil { itemCount() > 0 }
        // Search for a word from the newest conversation's name.
        val name = viewProperty {
            recyclerOf(it)!!.findViewHolderForAdapterPosition(0)!!
                .itemView.findViewById<android.widget.TextView>(R.id.name).text.toString()
        }
        val word = name.split(" ").first { it.length >= 3 }
        onActivity { searchItem(it).expandActionView() }
        onActivity { searchView(it).setQuery(word, false) }
        // Every visible row mentions the word in its name or matching snippet.
        waitUntil {
            viewProperty { activity ->
                val recycler = recyclerOf(activity) ?: return@viewProperty false
                recycler.childCount > 0 && (0 until recycler.childCount).all { i ->
                    val row = recycler.getChildAt(i)
                    val text = row.findViewById<android.widget.TextView>(R.id.name).text.toString() + " " +
                        row.findViewById<android.widget.TextView>(R.id.snippet).text.toString()
                    text.contains(word, ignoreCase = true)
                }
            }
        }
        onActivity { searchItem(it).collapseActionView() }
    }

    @Test
    fun switchingFolderAndBackStartsAtTop() {
        waitUntil { itemCount() > 0 }
        assumeTrue("Needs a scrollable inbox", viewProperty { recyclerOf(it)!!.canScrollVertically(1) })

        scrollDown()
        waitUntil { firstVisible() > 0 }

        selectDrawerItem(R.id.navSent)
        selectDrawerItem(R.id.navInbox)
        waitUntil { firstVisible() == 0 }
        assertEquals(0, firstVisible())
    }

    @Test
    fun switchingSimTabAndBackStartsAtTop() {
        waitUntil { itemCount() > 0 }
        assumeTrue("Needs two tabs", viewProperty { pager(it).adapter!!.itemCount >= 2 })
        assumeTrue("Needs a scrollable inbox", viewProperty { recyclerOf(it)!!.canScrollVertically(1) })

        scrollDown()
        waitUntil { firstVisible() > 0 }

        onActivity { pager(it).setCurrentItem(1, false) }
        waitUntil { viewProperty { pager(it).currentItem } == 1 }
        onActivity { pager(it).setCurrentItem(0, false) }
        waitUntil { viewProperty { pager(it).currentItem } == 0 && firstVisible() == 0 }
        assertEquals(0, firstVisible())
    }

    private fun scrollDown() = onActivity { activity ->
        val recycler = recyclerOf(activity)!!
        recycler.scrollToPosition(recycler.adapter!!.itemCount - 1)
    }

    private fun selectDrawerItem(id: Int) {
        onActivity { activity ->
            val nav = activity.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navView)
            nav.menu.performIdentifierAction(id, 0)
        }
        waitUntil { viewProperty { !it.findViewById<DrawerLayout>(R.id.drawerLayout).isDrawerOpen(android.view.Gravity.START) } }
        SystemClock.sleep(500)
    }

    private fun firstVisible(): Int = viewProperty {
        (recyclerOf(it)?.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: -1
    }

    private fun pager(activity: android.app.Activity) = activity.findViewById<ViewPager2>(R.id.pager)

    private fun searchItem(activity: MainActivity) =
        activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .menu.findItem(R.id.actionSearch)

    private fun searchView(activity: MainActivity) = searchItem(activity).actionView as SearchView

    /** The resumed pager page's root view. Call on the main thread. */
    private fun pageOf(activity: android.app.Activity): View? =
        (activity as MainActivity).supportFragmentManager.fragments
            .firstOrNull { it.isResumed && it.view?.findViewById<RecyclerView>(R.id.recycler) != null }
            ?.view

    private fun recyclerOf(activity: android.app.Activity): RecyclerView? =
        pageOf(activity)?.findViewById(R.id.recycler)

    private fun itemCount(): Int = viewProperty { recyclerOf(it)?.adapter?.itemCount ?: 0 }

    private fun onActivity(block: (MainActivity) -> Unit) {
        activityRule.scenario.onActivity { block(it) }
    }

    private fun <T> viewProperty(block: (android.app.Activity) -> T): T {
        var result: T? = null
        activityRule.scenario.onActivity { activity -> result = block(activity) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(timeoutMs: Long = 8000, condition: () -> Boolean) {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue("Condition not met within $timeoutMs ms", condition())
    }
}
