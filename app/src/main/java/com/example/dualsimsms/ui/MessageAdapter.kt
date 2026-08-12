package com.example.dualsimsms.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dualsimsms.R
import com.example.dualsimsms.databinding.ItemMessageBinding
import com.example.dualsimsms.model.Message
import com.example.dualsimsms.model.MessageDeliveryState
import com.example.dualsimsms.util.TimeFormatter
import com.google.android.material.color.MaterialColors

class MessageAdapter(
    private val colorResolver: (Int?) -> Int,
    private val simLabelResolver: (Int?) -> String?
) : ListAdapter<Message, MessageAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        applyMaxBubbleWidth(binding, parent.width)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Rebind bubbles whose SIM labels/colors changed without changing messages. */
    fun refreshSimPresentation() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Keep the cap correct across rotations/resizes without relying on rebind.
            binding.root.doOnLayout {
                applyMaxBubbleWidth(binding, binding.root.width)
            }
        }

        fun bind(message: Message) {
            applyMaxBubbleWidth(binding, binding.root.width)
            binding.body.text = message.body
            binding.time.text = TimeFormatter.relative(message.date)
            renderDeliveryState(message.deliveryState)

            binding.row.gravity = if (message.isIncoming) Gravity.START else Gravity.END
            val context = binding.root.context
            val bubbleColor = MaterialColors.getColor(
                binding.bubble,
                if (message.isIncoming) {
                    com.google.android.material.R.attr.colorSurfaceContainerHighest
                } else {
                    com.google.android.material.R.attr.colorPrimaryContainer
                }
            )
            binding.bubble.background = GradientDrawable().apply {
                cornerRadius = 20 * context.resources.displayMetrics.density
                setColor(bubbleColor)
            }

            val subId = message.subId
            val label = simLabelResolver(subId)
            binding.simBadge.isVisible = subId != null && label != null
            if (binding.simBadge.isVisible) {
                binding.simBadge.text = label
                binding.simBadge.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                binding.simBadge.setTypeface(null, Typeface.BOLD)
                binding.simBadge.background = GradientDrawable().apply {
                    cornerRadius = 10 * context.resources.displayMetrics.density
                    setColor(colorResolver(subId))
                }
                binding.simBadge.contentDescription =
                    context.getString(R.string.content_description_sim_badge, label)
            }
        }

        private fun renderDeliveryState(state: MessageDeliveryState) {
            val indicator = binding.statusIndicator
            val context = binding.root.context
            indicator.isVisible = state == MessageDeliveryState.SENT ||
                state == MessageDeliveryState.DELIVERED ||
                state == MessageDeliveryState.FAILED
            when (state) {
                MessageDeliveryState.SENT -> {
                    indicator.text = "✓"
                    indicator.contentDescription = context.getString(R.string.message_status_sent)
                    indicator.setTextColor(
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimaryContainer)
                    )
                }
                MessageDeliveryState.DELIVERED -> {
                    indicator.text = "✓✓"
                    indicator.contentDescription = context.getString(R.string.message_status_delivered)
                    indicator.setTextColor(
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                    )
                }
                MessageDeliveryState.FAILED -> {
                    indicator.text = "×"
                    indicator.contentDescription = context.getString(R.string.message_status_failed)
                    indicator.setTextColor(
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorError)
                    )
                }
                MessageDeliveryState.NONE, MessageDeliveryState.SENDING -> {
                    indicator.text = ""
                    indicator.contentDescription = null
                }
            }
        }
    }

    private fun applyMaxBubbleWidth(binding: ItemMessageBinding, availableWidthPx: Int) {
        if (availableWidthPx <= 0) return
        // View#maxWidth is a TextView API, not a generic View one; capping the body
        // text caps the wrap-content bubble as a whole.
        val maxWidth = (availableWidthPx * MAX_BUBBLE_WIDTH_FRACTION).toInt()
        if (binding.body.maxWidth != maxWidth) {
            binding.body.maxWidth = maxWidth
        }
    }

    private companion object {
        /** Bubbles may span at most this fraction of the available row width. */
        private const val MAX_BUBBLE_WIDTH_FRACTION = 0.8f

        val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean =
                oldItem == newItem
        }
    }
}
