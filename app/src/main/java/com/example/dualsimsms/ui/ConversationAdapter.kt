package com.example.dualsimsms.ui

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dualsimsms.R
import com.example.dualsimsms.databinding.ItemConversationBinding
import com.example.dualsimsms.model.Conversation
import com.example.dualsimsms.util.TimeFormatter
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR

class ConversationAdapter(
    private val colorResolver: (Int?) -> Int,
    private val simLabelResolver: (Int?) -> String?,
    private val showSimBadgeResolver: () -> Boolean,
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Search text to highlight in names and snippets; blank for none. */
    var query: String = ""
        set(value) {
            if (field == value) return
            field = value
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
        }

    /** Rebind rows whose SIM labels/colors changed without changing messages. */
    fun refreshSimPresentation() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation) {
            val context = binding.root.context
            val name = conversation.contactName ?: conversation.address
            binding.name.text = highlight(name)
            binding.time.text = TimeFormatter.listTimestamp(context, conversation.date)
            binding.snippet.text = highlight(snippetAroundMatch(conversation.snippet))
            Avatars.bind(binding.avatar, name, colorKey = conversation.address)

            val unread = conversation.unreadCount > 0
            binding.unreadBadge.isVisible = unread
            if (unread) {
                binding.unreadBadge.text = if (conversation.unreadCount > 99) {
                    context.getString(R.string.unread_badge_overflow)
                } else {
                    conversation.unreadCount.toString()
                }
                binding.unreadBadge.contentDescription =
                    context.getString(R.string.unread_count, conversation.unreadCount)
            }
            val emphasis = if (unread) Typeface.BOLD else Typeface.NORMAL
            binding.name.setTypeface(Typeface.create(binding.name.typeface, emphasis))
            binding.snippet.setTypeface(Typeface.create(binding.snippet.typeface, emphasis))
            binding.time.setTypeface(Typeface.create(binding.time.typeface, emphasis))
            binding.snippet.setTextColor(
                MaterialColors.getColor(
                    binding.snippet,
                    if (unread) MaterialR.attr.colorOnSurface else MaterialR.attr.colorOnSurfaceVariant
                )
            )
            binding.time.setTextColor(
                MaterialColors.getColor(
                    binding.time,
                    if (unread) MaterialR.attr.colorPrimary else MaterialR.attr.colorOnSurfaceVariant
                )
            )

            val subId = conversation.subId
            val label = simLabelResolver(subId)
            binding.simBadge.isVisible = showSimBadgeResolver() && subId != null && label != null
            if (binding.simBadge.isVisible) {
                binding.simBadge.text = label
                binding.simBadge.setSimDot(colorResolver(subId))
                binding.simBadge.contentDescription =
                    context.getString(R.string.content_description_sim_badge, label)
            }

            binding.root.setOnClickListener { onClick(conversation) }
        }

        /** Keeps a match deep inside a long message on the single snippet line. */
        private fun snippetAroundMatch(snippet: String): String {
            val index = if (query.isBlank()) -1 else snippet.indexOf(query, ignoreCase = true)
            if (index <= SNIPPET_LEAD) return snippet
            // Start at a word boundary just before the match when there is one.
            val from = index - SNIPPET_LEAD
            val space = snippet.indexOf(' ', from)
            val start = if (space in from until index) space + 1 else from
            return "…" + snippet.substring(start)
        }

        private fun highlight(text: String): CharSequence {
            if (query.isBlank()) return text
            val spannable = SpannableString(text)
            val color = MaterialColors.getColor(binding.root, MaterialR.attr.colorPrimary)
            var index = text.indexOf(query, ignoreCase = true)
            while (index >= 0) {
                val end = index + query.length
                spannable.setSpan(ForegroundColorSpan(color), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                index = text.indexOf(query, end, ignoreCase = true)
            }
            return spannable
        }
    }

    private companion object {
        /** Characters of context kept before a search match in a snippet. */
        const val SNIPPET_LEAD = 10

        val DIFF = object : DiffUtil.ItemCallback<Conversation>() {
            override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean =
                oldItem.threadId == newItem.threadId && oldItem.address == newItem.address

            override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean =
                oldItem == newItem
        }
    }
}
