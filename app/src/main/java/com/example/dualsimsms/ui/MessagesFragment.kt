package com.example.dualsimsms.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dualsimsms.R
import com.example.dualsimsms.data.Folder
import com.example.dualsimsms.databinding.FragmentMessagesBinding
import com.example.dualsimsms.model.Conversation
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * One pager page per SIM. Shows the conversations of the currently selected
 * folder (from the drawer), filtered to this page's subscription id.
 * A null [subId] is the "All SIMs" page.
 */
class SimMessagesFragment(private val subId: Int?) : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ConversationAdapter

    /** Identity of the newest row shown; a change means a new message landed. */
    private var topRow: Any? = null

    /** Set when the folder or search changed, so its first list opens at the top. */
    private var scrollToTopOnNextList = false

    private val mainActivity: MainActivity
        get() = requireActivity() as MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ConversationAdapter(
            colorResolver = mainActivity.mainViewModel::colorFor,
            simLabelResolver = mainActivity.mainViewModel::simLabel,
            showSimBadgeResolver = { subId == null },
            onClick = ::openConversation
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                mainActivity.onConversationListScrolled(dy, recyclerView.canScrollVertically(-1))
            }
        })
        binding.errorAction.setOnClickListener { mainActivity.requestSmsPermissions() }

        viewLifecycleOwner.lifecycleScope.launch {
            mainActivity.mainViewModel.conversationsFor(subId).collect { state ->
                render(state)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            // A different folder or search is a different screen: start at the top.
            combine(
                mainActivity.mainViewModel.selectedFolder,
                mainActivity.mainViewModel.searchQuery
            ) { folder, query -> folder to query.trim() }.drop(1).collect {
                scrollToTopOnNextList = true
                scrollToTop()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                mainActivity.mainViewModel.settings,
                mainActivity.mainViewModel.subscriptions
            ) { _, _ -> Unit }.collect {
                adapter.refreshSimPresentation()
            }
        }
    }

    /**
     * Pager pages resume when they become the visible SIM, and every page
     * resumes when returning from a conversation or Settings. Always open on
     * the newest conversation.
     */
    override fun onResume() {
        super.onResume()
        scrollToTop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: UiState<List<Conversation>>) {
        binding.recycler.isVisible = state is UiState.Success
        binding.emptyView.isVisible = state is UiState.Empty
        binding.errorView.isVisible = state is UiState.Error
        val query = mainActivity.mainViewModel.searchQuery.value.trim()
        if (state is UiState.Empty) {
            if (query.isNotEmpty()) {
                binding.emptyIcon.setImageResource(R.drawable.ic_search)
                binding.emptyTitle.setText(R.string.empty_search_title)
                binding.emptyText.text = getString(R.string.empty_search, query)
            } else {
                val folder = mainActivity.mainViewModel.selectedFolder.value
                val empty = if (subId == null) emptyStateFor(folder) else EmptyState(
                    R.drawable.ic_sim_card, R.string.empty_sim_filter_title, R.string.empty_sim_filter
                )
                binding.emptyIcon.setImageResource(empty.icon)
                binding.emptyTitle.setText(empty.title)
                binding.emptyText.setText(empty.body)
            }
        }
        adapter.query = query
        if (state is UiState.Success) {
            val top = state.data.first().let { Triple(it.threadId, it.address, it.date) }
            // A new message moves its conversation to the top; follow it there
            // even when reading further down the list.
            val jump = scrollToTopOnNextList || top != topRow
            topRow = top
            scrollToTopOnNextList = false
            adapter.submitList(state.data) { if (jump) scrollToTop() }
        } else {
            topRow = null
            // Do not keep the previous folder's adapter data behind an empty
            // or error view; it can flash briefly on the next folder switch.
            adapter.submitList(emptyList())
        }
    }

    private fun scrollToTop() {
        val recycler = _binding?.recycler ?: return
        recycler.stopScroll()
        recycler.scrollToPosition(0)
        mainActivity.onConversationListScrolled(0, canScrollUp = false)
    }

    private class EmptyState(val icon: Int, val title: Int, val body: Int)

    private fun emptyStateFor(folder: Folder): EmptyState = when (folder) {
        Folder.INBOX -> EmptyState(R.drawable.ic_inbox, R.string.empty_inbox_title, R.string.empty_inbox)
        Folder.SENT -> EmptyState(R.drawable.ic_send, R.string.empty_sent_title, R.string.empty_sent)
        Folder.DRAFTS -> EmptyState(R.drawable.ic_drafts, R.string.empty_drafts_title, R.string.empty_drafts)
    }

    private fun openConversation(conversation: Conversation) {
        ConversationActivity.start(
            requireContext(),
            conversation.threadId,
            conversation.address,
            conversation.subId
        )
    }
}
