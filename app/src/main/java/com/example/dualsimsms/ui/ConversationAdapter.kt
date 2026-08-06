package com.example.dualsimsms.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dualsimsms.R
import com.example.dualsimsms.databinding.ItemConversationBinding
import com.example.dualsimsms.model.Conversation
import com.example.dualsimsms.util.SimDefaults
import com.example.dualsimsms.util.TimeFormatter

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
            val name = conversation.contactName ?: conversation.address
            binding.name.text = name
            binding.time.text = TimeFormatter.relative(conversation.date)
            binding.snippet.text = conversation.snippet

            binding.avatar.text = name.firstOrNull()?.uppercase() ?: "#"
            binding.avatar.background = avatarBackground(name)

            if (conversation.unreadCount > 0) {
                binding.unreadBadge.isVisible = true
                binding.unreadBadge.text = binding.root.context.getString(
                    R.string.unread_count, conversation.unreadCount
                )
                binding.unreadBadge.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.unread_badge_text)
                )
                binding.unreadBadge.background = rounded(
                    ContextCompat.getColor(binding.root.context, R.color.unread_badge_background)
                )
                binding.name.setTypeface(null, android.graphics.Typeface.BOLD)
                binding.snippet.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                binding.unreadBadge.isVisible = false
                binding.name.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.snippet.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            val showBadge = showSimBadgeResolver()
            val subId = conversation.subId
            val label = simLabelResolver(subId)
            binding.simBadge.isVisible = showBadge && subId != null && label != null
            if (binding.simBadge.isVisible) {
                binding.simBadge.text = label
                binding.simBadge.setTextColor(ContextCompat.getColor(binding.root.context, android.R.color.white))
                binding.simBadge.background = rounded(colorResolver(subId))
                binding.simBadge.contentDescription =
                    binding.root.context.getString(R.string.content_description_sim_badge, label)
            }

            binding.root.setOnClickListener { onClick(conversation) }
        }

        private fun avatarBackground(name: String): GradientDrawable {
            val palette = SimDefaults.PALETTE
            val color = palette[Math.abs(name.hashCode()) % palette.size]
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }

        private fun rounded(color: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * binding.root.resources.displayMetrics.density
                setColor(color)
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
