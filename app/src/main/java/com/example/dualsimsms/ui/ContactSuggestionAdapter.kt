package com.example.dualsimsms.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dualsimsms.R
import com.example.dualsimsms.data.ContactSuggestion

class ContactSuggestionAdapter(
    private val onClick: (ContactSuggestion) -> Unit
) : ListAdapter<ContactSuggestion, ContactSuggestionAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = TextView(parent.context).apply {
            setPadding(
                dp(16, parent), dp(12, parent), dp(16, parent), dp(12, parent)
            )
            textSize = 16f
            isClickable = true
            isFocusable = true
            background = null
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val textView: TextView) :
        RecyclerView.ViewHolder(textView) {

        fun bind(suggestion: ContactSuggestion) {
            textView.text = textView.context.getString(
                R.string.contact_suggestion_format, suggestion.name, suggestion.number
            )
            textView.setOnClickListener { onClick(suggestion) }
        }
    }

    private fun dp(value: Int, parent: ViewGroup): Int =
        (value * parent.resources.displayMetrics.density).toInt()

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ContactSuggestion>() {
            override fun areItemsTheSame(oldItem: ContactSuggestion, newItem: ContactSuggestion): Boolean =
                oldItem.number == newItem.number

            override fun areContentsTheSame(oldItem: ContactSuggestion, newItem: ContactSuggestion): Boolean =
                oldItem == newItem
        }
    }
}
