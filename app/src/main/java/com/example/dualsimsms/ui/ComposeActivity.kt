package com.example.dualsimsms.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dualsimsms.R
import com.example.dualsimsms.databinding.ActivityComposeBinding
import com.example.dualsimsms.data.ContactSuggestion
import com.example.dualsimsms.model.SimProfile
import com.example.dualsimsms.telephony.SmsExtras
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * New-message screen: recipient with contact suggestions, character and
 * segment counts, SIM selector, draft auto-save and sending.
 */
class ComposeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComposeBinding
    private lateinit var suggestionAdapter: ContactSuggestionAdapter

    private val viewModel: ComposeViewModel by viewModels { ComposeViewModel.Factory }

    private var suggestionJob: Job? = null
    private var selectedSuggestion: ContactSuggestion? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        applyInsets()

        suggestionAdapter = ContactSuggestionAdapter { suggestion ->
            selectedSuggestion = suggestion
            binding.recipientInput.setText(suggestion.name)
            binding.recipientInput.setSelection(suggestion.name.length)
            binding.recipientInput.error = null
            binding.suggestions.isVisible = false
        }
        binding.suggestions.layoutManager = LinearLayoutManager(this)
        binding.suggestions.adapter = suggestionAdapter

        intent.getStringExtra(SmsExtras.EXTRA_NUMBER)?.let { number ->
            binding.recipientInput.setText(number)
        }

        binding.recipientInput.addTextChangedListener(recipientWatcher)
        binding.bodyInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    viewModel.onTextChanged(recipient(), s?.toString().orEmpty())
                    updateCounts()
                }
            }
        )

        binding.sendButton.setOnClickListener {
            val recipient = recipient()
            if (selectedSuggestion == null && !looksLikePhoneNumber(recipient)) {
                binding.recipientInput.error = getString(R.string.recipient_invalid)
                return@setOnClickListener
            }
            binding.recipientInput.error = null
            viewModel.send(recipient, binding.bodyInput.text.toString())
        }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                renderSimChips(state.subscriptions)
                binding.sendButton.isEnabled = !state.sending &&
                    state.selectedSubId != null
                state.message?.let { message ->
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    viewModel.consumeMessage()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.sent.collect { destination ->
                ConversationActivity.start(
                    this@ComposeActivity,
                    destination.threadId,
                    destination.address,
                    destination.subId
                )
                finish()
            }
        }
    }

    private fun recipient(): String = selectedSuggestion?.number
        ?: binding.recipientInput.text?.toString()?.trim().orEmpty()

    private fun looksLikePhoneNumber(value: String): Boolean =
        value.any(Char::isDigit) && value.all { character ->
            character.isDigit() || character in "+-() .#*"
        }

    private val recipientWatcher = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) {
            val query = s?.toString().orEmpty()
            if (selectedSuggestion?.name != query) selectedSuggestion = null
            binding.recipientInput.error = null
            viewModel.onTextChanged(
                recipient(),
                binding.bodyInput.text?.toString().orEmpty()
            )
            updateCounts()
            suggestionJob?.cancel()
            if (selectedSuggestion != null) {
                binding.suggestions.isVisible = false
                return
            }
            val hasPermission = ContextCompat.checkSelfPermission(
                this@ComposeActivity, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission || query.isBlank()) {
                binding.suggestions.isVisible = false
                return
            }
            suggestionJob = lifecycleScope.launch {
                delay(250)
                val suggestions = withContext(Dispatchers.IO) {
                    com.example.dualsimsms.SmsAppContainer.suggest(this@ComposeActivity, query)
                }
                if (query != recipient()) return@launch
                if (suggestions.isEmpty()) {
                    binding.suggestions.isVisible = false
                } else {
                    suggestionAdapter.submitList(suggestions)
                    binding.suggestions.isVisible = true
                }
            }
        }
    }

    private fun renderSimChips(subscriptions: List<SimProfile>) {
        binding.simChipGroup.removeAllViews()
        val settings = viewModel.state.value.settings
        subscriptions.forEach { sub ->
            val label = settings.sims[sub.subscriptionId]?.customName
                ?: com.example.dualsimsms.util.SimDefaults.defaultName(sub.slotIndex)
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                setOnClickListener {
                    viewModel.selectSub(sub.subscriptionId)
                }
            }
            binding.simChipGroup.addView(chip)
            if (sub.subscriptionId == viewModel.state.value.selectedSubId) {
                chip.isChecked = true
            }
        }
    }

    private fun updateCounts() {
        val body = binding.bodyInput.text.toString()
        val segments = runCatching {
            SmsManager.getDefault().divideMessage(body).size
        }.getOrDefault(if (body.isEmpty()) 0 else 1)
        binding.countLabel.text = getString(R.string.character_count, body.length, segments)
    }

    private fun applyInsets() {
        // Grow the app bar by the status-bar inset so the toolbar keeps its
        // full height instead of being squeezed behind the status bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    companion object {
        fun intent(context: Context, number: String? = null): Intent =
            Intent(context, ComposeActivity::class.java).apply {
                if (number != null) putExtra(SmsExtras.EXTRA_NUMBER, number)
            }

        fun start(context: Context, number: String? = null) {
            context.startActivity(intent(context, number))
        }
    }
}
