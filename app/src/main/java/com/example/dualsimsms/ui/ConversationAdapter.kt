package com.example.dualsimsms.ui

import android.graphics.Typeface
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

    /** Rebind rows whose SIM labels/colors changed without changing messages. */
    fun refreshSimPresentation() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation) {
            val context = binding.root.context
            val name = conversation.contactName ?: conversation.address
            binding.name.text = name
            binding.time.text = TimeFormatter.listTimestamp(context, conversation.date)
            binding.snippet.text = conversation.snippet
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
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Conversation>() {
            override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean =
                oldItem.threadId == newItem.threadId && oldItem.address == newItem.address

            override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean =
                oldItem == newItem
        }
    }
}
