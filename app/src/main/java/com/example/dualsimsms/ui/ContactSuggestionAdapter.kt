package com.example.dualsimsms.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dualsimsms.R
import com.example.dualsimsms.data.ContactSuggestion
import com.example.dualsimsms.databinding.ItemContactSuggestionBinding

class ContactSuggestionAdapter(
    private val onClick: (ContactSuggestion) -> Unit
) : ListAdapter<ContactSuggestion, ContactSuggestionAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemContactSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemContactSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(suggestion: ContactSuggestion) {
            binding.name.text = suggestion.name
            binding.number.text = suggestion.number
            Avatars.bind(binding.avatar, suggestion.name, colorKey = suggestion.number)
            binding.root.contentDescription = binding.root.context.getString(
                R.string.contact_suggestion_format, suggestion.name, suggestion.number
            )
            binding.root.setOnClickListener { onClick(suggestion) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ContactSuggestion>() {
            override fun areItemsTheSame(oldItem: ContactSuggestion, newItem: ContactSuggestion): Boolean =
                oldItem.number == newItem.number

            override fun areContentsTheSame(oldItem: ContactSuggestion, newItem: ContactSuggestion): Boolean =
                oldItem == newItem
        }
    }
}
