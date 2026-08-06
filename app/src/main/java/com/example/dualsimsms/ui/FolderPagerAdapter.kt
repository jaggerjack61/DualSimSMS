package com.example.dualsimsms.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Pages over the SIM selector: position 0 is "All SIMs", each following
 * position is one subscription. Swiping left/right switches the SIM while
 * the selected folder (Inbox/Sent/Drafts) stays put.
 */
class SimPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private var tabs: List<TabModel> = listOf(TabModel(null, ""))

    fun updateTabs(newTabs: List<TabModel>) {
        tabs = newTabs
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = tabs.size

    override fun getItemId(position: Int): Long = tabs[position].subId?.toLong() ?: 0L

    override fun containsItem(itemId: Long): Boolean =
        tabs.any { (it.subId?.toLong() ?: 0L) == itemId }

    override fun createFragment(position: Int): Fragment =
        SimMessagesFragment(tabs[position].subId)
}
