package com.example.dualsimsms.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dualsimsms.R
import com.example.dualsimsms.data.SettingsRepository
import com.example.dualsimsms.databinding.ActivityConversationBinding
import com.example.dualsimsms.model.SimProfile
import com.example.dualsimsms.telephony.SmsExtras
import com.example.dualsimsms.util.SimDefaults
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens a conversation thread: message bubbles with SIM badges, mark-as-read,
 * draft auto-save and an explicit-SIM send bar.
 */
class ConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationBinding
    private lateinit var messageAdapter: MessageAdapter

    private val threadId: Long by lazy { intent.getLongExtra(SmsExtras.EXTRA_THREAD_ID, -1L) }
    private val address: String by lazy { intent.getStringExtra(SmsExtras.EXTRA_ADDRESS) ?: "" }
    private val initialSubId: Int? by lazy {
        intent.getIntExtra(SmsExtras.EXTRA_SUB_ID, -1).takeIf { it > 0 }
    }

    private val viewModel: ConversationViewModel by viewModels {
        ConversationViewModel.factory(threadId, address, initialSubId)
    }

    private var simSubIds: List<Int> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }

        applyInsets()

        messageAdapter = MessageAdapter(
            colorResolver = viewModel::colorFor,
            simLabelResolver = viewModel::simLabel,
            onCopied = ::showCopiedConfirmation
        )
        binding.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recycler.adapter = messageAdapter
        binding.recycler.itemAnimator?.changeDuration = 0

        binding.simSpinner.setOnClickListener { showSimPicker() }

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
                messageAdapter.submitMessages(messages) {
                    if (messageAdapter.itemCount > 0) {
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
                renderSimSelector()
            }
        }
        lifecycleScope.launch {
            viewModel.snackbar.collect { message ->
                if (message != null) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                        .setAnchorView(binding.composeBar)
                        .show()
                    viewModel.consumeSnackbar()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.subscriptions.collect { subscriptions ->
                simSubIds = subscriptions.map { it.subscriptionId }
                // If the chosen SIM was removed, fall back to one that exists.
                val current = viewModel.compose.value.selectedSubId
                if (current != null && current !in simSubIds) {
                    subscriptions.firstOrNull()?.let { viewModel.selectSub(it.subscriptionId) }
                }
                renderSimSelector()
                messageAdapter.refreshSimPresentation()
            }
        }
        lifecycleScope.launch {
            viewModel.settings.collect {
                renderSimSelector()
                messageAdapter.refreshSimPresentation()
            }
        }

        renderHeader(name = null)
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                com.example.dualsimsms.SmsAppContainer.contactName(this@ConversationActivity, address)
            }
            if (name != null) renderHeader(name)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (isDialable(address)) {
            menu.add(Menu.NONE, MENU_CALL, Menu.NONE, R.string.action_call)
                .setIcon(R.drawable.ic_call)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_CALL) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", address, null)))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun renderHeader(name: String?) {
        val formatted = formatNumber(address)
        binding.toolbarTitle.text = name ?: formatted
        binding.toolbarSubtitle.text = formatted
        binding.toolbarSubtitle.isVisible = name != null
        Avatars.bind(binding.toolbarAvatar, name ?: address, colorKey = address)
    }

    private fun renderSimSelector() {
        val subs = viewModel.subscriptions.value
        val selected = viewModel.compose.value.selectedSubId
        val label = when {
            subs.isEmpty() -> getString(R.string.no_sim)
            else -> labelFor(subs.firstOrNull { it.subscriptionId == selected } ?: subs.first())
        }
        binding.simSpinner.text = label
        binding.simSpinner.setSimDot(viewModel.colorFor(selected))
        binding.simSpinner.contentDescription = getString(R.string.sim_selector_description, label)
        binding.simSpinner.isEnabled = subs.size > 1
        // A single SIM needs no picker affordance.
        val arrow = if (subs.size > 1) binding.simSpinner.compoundDrawablesRelative[2] else null
        if (subs.size <= 1) {
            val drawables = binding.simSpinner.compoundDrawablesRelative
            binding.simSpinner.setCompoundDrawablesRelativeWithIntrinsicBounds(
                drawables[0], null, null, null
            )
        } else if (arrow == null) {
            val drawables = binding.simSpinner.compoundDrawablesRelative
            binding.simSpinner.setCompoundDrawablesRelativeWithIntrinsicBounds(
                drawables[0], null, AppCompatResources.getDrawable(this, R.drawable.ic_arrow_drop_down), null
            )
        }
    }

    private fun showSimPicker() {
        val subs = viewModel.subscriptions.value
        if (subs.size <= 1) return
        val popup = PopupMenu(this, binding.simSpinner)
        subs.forEachIndexed { index, sub ->
            popup.menu.add(Menu.NONE, index, index, labelFor(sub)).apply {
                isCheckable = true
                isChecked = sub.subscriptionId == viewModel.compose.value.selectedSubId
            }
        }
        popup.menu.setGroupCheckable(Menu.NONE, true, true)
        popup.setOnMenuItemClickListener { item ->
            simSubIds.getOrNull(item.itemId)?.let(viewModel::selectSub)
            true
        }
        popup.show()
    }

    private fun labelFor(sub: SimProfile): String {
        val settings: SettingsRepository.AppSettings = viewModel.settings.value
        return settings.sims[sub.subscriptionId]?.customName ?: SimDefaults.defaultName(sub.slotIndex)
    }

    private fun updateCounts(body: String) {
        val segments = runCatching {
            SmsManager.getDefault().divideMessage(body).size
        }.getOrDefault(if (body.isEmpty()) 0 else 1)
        binding.countLabel.text =
            resources.getQuantityString(R.plurals.character_count, body.length, body.length, segments)
        binding.countLabel.isVisible = body.isNotEmpty()
    }

    private fun showCopiedConfirmation() {
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Snackbar.make(binding.root, R.string.message_copied, Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.composeBar)
                .show()
        }
    }

    private fun formatNumber(value: String): String =
        if (isDialable(value)) {
            PhoneNumberUtils.formatNumber(value, java.util.Locale.getDefault().country) ?: value
        } else {
            value
        }

    private fun isDialable(value: String): Boolean =
        value.count(Char::isDigit) >= 3 && value.all { it.isDigit() || it in "+-() ." }

    private fun applyInsets() {
        // Grow the app bar by the status-bar inset so the toolbar keeps its
        // full height instead of being squeezed behind the status bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        // Lift the send bar above both the navigation bar and the keyboard.
        val baseBottom = binding.composeBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.composeBar) { v, insets ->
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            ).bottom
            v.updatePadding(bottom = baseBottom + bottom)
            insets
        }
    }

    companion object {
        private const val MENU_CALL = 1

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
