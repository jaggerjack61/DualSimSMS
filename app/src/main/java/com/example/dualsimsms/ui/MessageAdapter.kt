package com.example.dualsimsms.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dualsimsms.R
import com.example.dualsimsms.databinding.ItemDayHeaderBinding
import com.example.dualsimsms.databinding.ItemMessageBinding
import com.example.dualsimsms.model.Message
import com.example.dualsimsms.model.MessageDeliveryState
import com.example.dualsimsms.util.ThreadLayout
import com.example.dualsimsms.util.TimeFormatter
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR

class MessageAdapter(
    private val colorResolver: (Int?) -> Int,
    private val simLabelResolver: (Int?) -> String?,
    private val onCopied: () -> Unit = {}
) : ListAdapter<ThreadLayout.Row, RecyclerView.ViewHolder>(DIFF) {

    fun submitMessages(messages: List<Message>, commitCallback: Runnable? = null) {
        submitList(ThreadLayout.build(messages), commitCallback)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ThreadLayout.DayHeader -> TYPE_DAY
        is ThreadLayout.Bubble -> TYPE_BUBBLE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DAY) {
            DayViewHolder(ItemDayHeaderBinding.inflate(inflater, parent, false))
        } else {
            val binding = ItemMessageBinding.inflate(inflater, parent, false)
            applyMaxBubbleWidth(binding, parent.width)
            BubbleViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ThreadLayout.DayHeader -> (holder as DayViewHolder).bind(row)
            is ThreadLayout.Bubble -> (holder as BubbleViewHolder).bind(row)
        }
    }

    /** Rebind bubbles whose SIM labels/colors changed without changing messages. */
    fun refreshSimPresentation() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    class DayViewHolder(private val binding: ItemDayHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ThreadLayout.DayHeader) {
            binding.dayLabel.text = TimeFormatter.dayHeader(binding.root.context, row.timestamp)
        }
    }

    inner class BubbleViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val density = binding.root.resources.displayMetrics.density

        init {
            // Keep the cap correct across rotations/resizes without relying on rebind.
            binding.root.doOnLayout {
                applyMaxBubbleWidth(binding, binding.root.width)
            }
        }

        fun bind(row: ThreadLayout.Bubble) {
            val message = row.message
            val context = binding.root.context
            applyMaxBubbleWidth(binding, binding.root.width)

            binding.body.text = message.body
            binding.row.gravity = if (message.isIncoming) Gravity.START else Gravity.END
            binding.row.updatePadding(top = ((if (row.joinsPrevious) 2 else 8) * density).toInt())

            val (bubbleAttr, textAttr, linkAttr) = if (message.isIncoming) {
                Triple(
                    MaterialR.attr.colorSurfaceContainerHigh,
                    MaterialR.attr.colorOnSurface,
                    MaterialR.attr.colorPrimary
                )
            } else {
                Triple(
                    MaterialR.attr.colorPrimary,
                    MaterialR.attr.colorOnPrimary,
                    MaterialR.attr.colorOnPrimary
                )
            }
            binding.body.setTextColor(MaterialColors.getColor(binding.body, textAttr))
            binding.body.setLinkTextColor(MaterialColors.getColor(binding.body, linkAttr))
            binding.body.background = bubbleShape(
                color = MaterialColors.getColor(binding.body, bubbleAttr),
                incoming = message.isIncoming,
                joinsPrevious = row.joinsPrevious,
                joinsNext = row.joinsNext
            )

            binding.meta.isVisible = row.showMeta
            if (row.showMeta) bindMeta(message)

            binding.body.setOnLongClickListener { view ->
                copyToClipboard(view, message.body)
                true
            }
        }

        private fun bindMeta(message: Message) {
            val context = binding.root.context
            val subId = message.subId
            val label = simLabelResolver(subId)
            binding.simBadge.isVisible = subId != null && label != null
            binding.metaSeparator.isVisible = binding.simBadge.isVisible
            if (binding.simBadge.isVisible) {
                binding.simBadge.text = label
                binding.simBadge.setSimDot(colorResolver(subId))
                binding.simBadge.contentDescription =
                    context.getString(R.string.content_description_sim_badge, label)
            }

            val state = message.deliveryState
            val variant = MaterialColors.getColor(binding.time, MaterialR.attr.colorOnSurfaceVariant)
            val error = MaterialColors.getColor(binding.time, MaterialR.attr.colorError)
            binding.time.text = when (state) {
                MessageDeliveryState.FAILED -> context.getString(R.string.message_status_failed)
                MessageDeliveryState.SENDING -> context.getString(R.string.message_status_sending)
                else -> TimeFormatter.clockTime(context, message.date)
            }
            binding.time.setTextColor(if (state == MessageDeliveryState.FAILED) error else variant)

            val indicator = binding.statusIndicator
            val (icon, description, tint) = when (state) {
                MessageDeliveryState.SENT -> Triple(R.drawable.ic_check, R.string.message_status_sent, variant)
                MessageDeliveryState.DELIVERED -> Triple(
                    R.drawable.ic_done_all, R.string.message_status_delivered,
                    MaterialColors.getColor(indicator, MaterialR.attr.colorPrimary)
                )
                MessageDeliveryState.FAILED -> Triple(R.drawable.ic_error, R.string.message_status_failed, error)
                MessageDeliveryState.SENDING -> Triple(R.drawable.ic_schedule, R.string.message_status_sending, variant)
                MessageDeliveryState.NONE -> Triple(0, 0, variant)
            }
            indicator.isVisible = icon != 0
            if (icon != 0) {
                indicator.setImageResource(icon)
                ImageViewCompat.setImageTintList(indicator, ColorStateList.valueOf(tint))
                // FAILED and SENDING already spell out their state in the time slot.
                indicator.contentDescription = if (
                    state == MessageDeliveryState.SENT || state == MessageDeliveryState.DELIVERED
                ) context.getString(description) else null
            }
        }

        /**
         * Rounded bubble whose corners tighten on the side that touches a
         * neighbour in the same group, so a run of messages reads as one block.
         */
        private fun bubbleShape(
            color: Int,
            incoming: Boolean,
            joinsPrevious: Boolean,
            joinsNext: Boolean
        ): GradientDrawable {
            val large = 20 * density
            val small = 6 * density
            val rtl = binding.root.layoutDirection == View.LAYOUT_DIRECTION_RTL
            // The "tail" side is start for incoming, end for outgoing; flip in RTL.
            val tailOnLeft = incoming != rtl
            val top = if (joinsPrevious) small else large
            val bottom = if (joinsNext) small else large
            val topLeft = if (tailOnLeft) top else large
            val bottomLeft = if (tailOnLeft) bottom else large
            val topRight = if (tailOnLeft) large else top
            val bottomRight = if (tailOnLeft) large else bottom
            return GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    topLeft, topLeft, topRight, topRight,
                    bottomRight, bottomRight, bottomLeft, bottomLeft
                )
                setColor(color)
            }
        }

        private fun copyToClipboard(view: View, text: String) {
            val clipboard = view.context.getSystemService(ClipboardManager::class.java) ?: return
            clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onCopied()
        }
    }

    private fun applyMaxBubbleWidth(binding: ItemMessageBinding, availableWidthPx: Int) {
        if (availableWidthPx <= 0) return
        // View#maxWidth is a TextView API, not a generic View one; the body
        // text view is the bubble, so capping it caps the bubble.
        val maxWidth = (availableWidthPx * MAX_BUBBLE_WIDTH_FRACTION).toInt()
        if (binding.body.maxWidth != maxWidth) {
            binding.body.maxWidth = maxWidth
        }
    }

    private companion object {
        /** Bubbles may span at most this fraction of the available row width. */
        private const val MAX_BUBBLE_WIDTH_FRACTION = 0.8f

        const val TYPE_DAY = 0
        const val TYPE_BUBBLE = 1

        val DIFF = object : DiffUtil.ItemCallback<ThreadLayout.Row>() {
            override fun areItemsTheSame(oldItem: ThreadLayout.Row, newItem: ThreadLayout.Row): Boolean =
                when {
                    oldItem is ThreadLayout.Bubble && newItem is ThreadLayout.Bubble ->
                        oldItem.message.id == newItem.message.id
                    oldItem is ThreadLayout.DayHeader && newItem is ThreadLayout.DayHeader ->
                        TimeFormatter.isSameDay(oldItem.timestamp, newItem.timestamp)
                    else -> false
                }

            override fun areContentsTheSame(oldItem: ThreadLayout.Row, newItem: ThreadLayout.Row): Boolean =
                oldItem == newItem
        }
    }
}
