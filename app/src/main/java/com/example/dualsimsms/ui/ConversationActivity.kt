package com.example.dualsimsms.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dualsimsms.R
import com.example.dualsimsms.databinding.ActivityConversationBinding
import com.example.dualsimsms.model.SimProfile
import com.example.dualsimsms.telephony.SmsExtras
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Opens a conversation thread: message bubbles with SIM badges, mark-as-read,
 * draft auto-save and an explicit-SIM send bar.
 */
class ConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationBinding
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var simAdapter: android.widget.ArrayAdapter<String>

    private val threadId: Long by lazy { intent.getLongExtra(SmsExtras.EXTRA_THREAD_ID, -1L) }
    private val address: String by lazy { intent.getStringExtra(SmsExtras.EXTRA_ADDRESS) ?: "" }
    private val initialSubId: Int? by lazy {
        intent.getIntExtra(SmsExtras.EXTRA_SUB_ID, -1).takeIf { it > 0 }
    }

    private val viewModel: ConversationViewModel by viewModels {
        ConversationViewModel.factory(threadId, address)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        applyInsets()

        messageAdapter = MessageAdapter(
            colorResolver = viewModel::colorFor,
            simLabelResolver = viewModel::simLabel
        )
        binding.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recycler.adapter = messageAdapter

        simAdapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item
        )
        binding.simSpinner.adapter = simAdapter
        binding.simSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val subId = simSubIds.getOrNull(position)
                if (subId != null) viewModel.selectSub(subId)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        binding.sendButton.setOnClickListener {
            viewModel.send()
        }

        binding.bodyInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    val text = s?.toString().orEmpty()
                    viewModel.onBodyChanged(text)
                    updateCounts(text)
                }
            }
        )

        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                messageAdapter.submitList(messages)
                binding.recycler.post {
                    if (messages.isNotEmpty()) {
                        binding.recycler.scrollToPosition(messageAdapter.itemCount - 1)
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.compose.collect { compose ->
                if (binding.bodyInput.text.toString() != compose.body) {
                    binding.bodyInput.setText(compose.body)
                    binding.bodyInput.setSelection(compose.body.length)
                }
                updateCounts(compose.body)
                binding.sendButton.isEnabled = !compose.sending &&
                    compose.body.isNotBlank() && compose.selectedSubId != null
            }
        }
        lifecycleScope.launch {
            viewModel.snackbar.collect { message ->
                if (message != null) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    viewModel.consumeSnackbar()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.subscriptions.collect { subscriptions ->
                renderSimSelector(subscriptions, viewModel.compose.value.selectedSubId ?: initialSubId)
                messageAdapter.refreshSimPresentation()
            }
        }
        lifecycleScope.launch {
            viewModel.settings.collect { settings ->
                val labels = simLabelsFor(settings)
                renderSimLabels(labels)
                messageAdapter.refreshSimPresentation()
            }
        }

        binding.toolbar.title = address
        lifecycleScope.launch {
            val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.dualsimsms.SmsAppContainer.contactName(this@ConversationActivity, address)
            }
            if (name != null) {
                binding.toolbar.title = name
                binding.toolbar.subtitle = address
            }
        }
    }

    private var simSubIds: List<Int> = emptyList()

    private fun renderSimSelector(subscriptions: List<SimProfile>, selectedSubId: Int?) {
        simSubIds = subscriptions.map { it.subscriptionId }
        renderSimLabels(simLabelsFor(viewModel.settings.value))
        val index = simSubIds.indexOf(selectedSubId).let {
            if (it >= 0) it else simSubIds.indexOfFirst { subId -> subId >= 0 }
        }
        if (index >= 0) binding.simSpinner.setSelection(index, false)
    }

    private fun renderSimLabels(labels: List<String>) {
        simAdapter.clear()
        simAdapter.addAll(labels)
    }

    private fun simLabelsFor(settings: com.example.dualsimsms.data.SettingsRepository.AppSettings): List<String> {
        val subs = viewModel.subscriptions.value
        if (subs.isEmpty()) return listOf(getString(R.string.no_sim))
        return subs.map { sub ->
            settings.sims[sub.subscriptionId]?.customName
                ?: com.example.dualsimsms.util.SimDefaults.defaultName(sub.slotIndex)
        }
    }

    private fun updateCounts(body: String) {
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.composeBar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    companion object {
        fun intent(context: Context, threadId: Long, address: String, subId: Int?): Intent =
            Intent(context, ConversationActivity::class.java).apply {
                putExtra(SmsExtras.EXTRA_THREAD_ID, threadId)
                putExtra(SmsExtras.EXTRA_ADDRESS, address)
                if (subId != null) putExtra(SmsExtras.EXTRA_SUB_ID, subId)
            }

        fun start(context: Context, threadId: Long, address: String, subId: Int?) {
            context.startActivity(intent(context, threadId, address, subId))
        }
    }
}
