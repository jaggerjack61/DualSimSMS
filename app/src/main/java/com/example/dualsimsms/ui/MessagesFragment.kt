package com.example.dualsimsms.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dualsimsms.R
import com.example.dualsimsms.data.Folder
import com.example.dualsimsms.databinding.FragmentMessagesBinding
import com.example.dualsimsms.model.Conversation
import kotlinx.coroutines.flow.combine
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
        binding.errorAction.setOnClickListener { mainActivity.requestSmsPermissions() }

        viewLifecycleOwner.lifecycleScope.launch {
            mainActivity.mainViewModel.conversationsFor(subId).collect { state ->
                render(state)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: UiState<List<Conversation>>) {
        binding.recycler.isVisible = state is UiState.Success
        binding.emptyView.isVisible = state is UiState.Empty
        binding.errorView.isVisible = state is UiState.Error
        if (state is UiState.Empty) {
            binding.emptyText.setText(
                if (subId == null) emptyTextFor(mainActivity.mainViewModel.selectedFolder.value)
                else R.string.empty_sim_filter
            )
        }
        if (state is UiState.Success) {
            adapter.submitList(state.data)
        } else {
            // Do not keep the previous folder's adapter data behind an empty
            // or error view; it can flash briefly on the next folder switch.
            adapter.submitList(emptyList())
        }
    }

    private fun emptyTextFor(folder: Folder): Int = when (folder) {
        Folder.INBOX -> R.string.empty_inbox
        Folder.SENT -> R.string.empty_sent
        Folder.DRAFTS -> R.string.empty_drafts
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
