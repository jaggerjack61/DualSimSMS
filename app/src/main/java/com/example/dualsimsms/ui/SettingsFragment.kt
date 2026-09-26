package com.example.dualsimsms.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dualsimsms.R
import com.example.dualsimsms.data.SettingsRepository
import com.example.dualsimsms.data.SettingsRepository.AppSettings
import com.example.dualsimsms.databinding.FragmentSettingsBinding
import com.example.dualsimsms.databinding.ItemSettingsSimBinding
import com.example.dualsimsms.model.SimProfile
import com.example.dualsimsms.util.SimDefaults
import kotlinx.coroutines.launch

/**
 * One card per active subscription: custom name (with reset) and a preset
 * color palette. Changes apply immediately to tabs, badges and compose
 * controls.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val mainViewModel: MainViewModel by activityViewModels { MainViewModel.Factory }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        renderNotificationState()
    }

    private lateinit var adapter: SimSettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SimSettingsAdapter(viewModel)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.defaultSmsButton.setOnClickListener {
            (activity as? MainActivity)?.requestSmsRole()
        }
        binding.notificationButton.setOnClickListener { openOrRequestNotifications() }
        renderNotificationState()

        // Keep the last card and a focused name field clear of the
        // navigation bar and keyboard.
        val baseBottom = binding.scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.scroll) { v, insets ->
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            ).bottom
            v.updatePadding(bottom = baseBottom + bottom)
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.isDefaultSmsHandler.collect(::renderDefaultSmsState)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.loadingIndicator.isVisible = state.isLoading
                binding.recycler.isVisible = !state.isLoading && state.subscriptions.isNotEmpty()
                binding.emptyView.isVisible = !state.isLoading && state.subscriptions.isEmpty()
                adapter.submitList(SimItem.all(state.subscriptions, state.settings))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshCapabilities()
        renderNotificationState()
    }

    private fun renderDefaultSmsState(isDefault: Boolean) {
        binding.defaultSmsStatus.setText(
            if (isDefault) R.string.settings_default_sms_selected
            else R.string.settings_default_sms_not_selected
        )
        binding.defaultSmsButton.setText(
            if (isDefault) R.string.settings_default_selected
            else R.string.settings_make_default
        )
        binding.defaultSmsButton.isEnabled = !isDefault
    }

    private fun renderNotificationState() {
        val currentBinding = _binding ?: return
        val enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        currentBinding.notificationStatus.setText(
            if (enabled) R.string.settings_notifications_enabled
            else R.string.settings_notifications_disabled
        )
        currentBinding.notificationButton.setText(
            if (enabled) R.string.settings_notification_settings
            else R.string.settings_enable_notifications
        )
    }

    private fun openOrRequestNotifications() {
        val context = requireContext()
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (permissionMissing) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            )
        }
    }

    override fun onDestroyView() {
        // Commit an in-progress name before navigation destroys the editor.
        binding.recycler.findFocus()?.clearFocus()
        binding.recycler.adapter = null
        super.onDestroyView()
        _binding = null
    }
}

private data class SimItem(
    val sub: SimProfile,
    val customName: String?,
    val colorArgb: Int?
) {
    companion object {
        fun all(subs: List<SimProfile>, settings: AppSettings): List<SimItem> =
            subs.map { sub ->
                val simSettings = settings.sims[sub.subscriptionId]
                SimItem(
                    sub = sub,
                    customName = simSettings?.customName,
                    colorArgb = simSettings?.colorArgb
                )
            }
    }
}

private class SimSettingsAdapter(
    private val viewModel: SettingsViewModel
) : ListAdapter<SimItem, SimSettingsAdapter.ViewHolder>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).sub.subscriptionId.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSettingsSimBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, viewModel)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.commitName()
        super.onViewRecycled(holder)
    }

    class ViewHolder(
        private val binding: ItemSettingsSimBinding,
        private val viewModel: SettingsViewModel
    ) : RecyclerView.ViewHolder(binding.root) {

        private var subId: Int = -1

        fun bind(item: SimItem) {
            val context = binding.root.context
            subId = item.sub.subscriptionId

            val simColor = item.colorArgb ?: SimDefaults.defaultColor(item.sub.slotIndex)
            binding.simTitle.text = item.customName ?: SimDefaults.defaultName(item.sub.slotIndex)
            binding.simIcon.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(simColor, 0x33))
            }
            ImageViewCompat.setImageTintList(binding.simIcon, ColorStateList.valueOf(simColor))
            binding.slotInfo.text = context.getString(
                R.string.settings_slot_info,
                item.sub.slotIndex + 1,
                item.sub.carrierName ?: item.sub.displayName
            )

            binding.nameField.removeTextChangedListener(nameWatcher)
            // Keep the raw text while the user is editing. Persisted names are
            // trimmed, so replacing focused text from DataStore on every key
            // stroke would make spaces impossible to enter.
            if (!binding.nameField.isFocused &&
                binding.nameField.text?.toString() != item.customName.orEmpty()
            ) {
                binding.nameField.setText(item.customName.orEmpty())
            }
            binding.nameInput.hint = context.getString(
                R.string.settings_name_hint, SimDefaults.defaultName(item.sub.slotIndex)
            )
            binding.nameField.addTextChangedListener(nameWatcher)
            binding.nameField.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) normalizeDisplayedName()
            }
            binding.nameField.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    binding.nameField.clearFocus()
                    true
                } else {
                    false
                }
            }

            binding.nameInput.isEndIconVisible = item.customName != null
            binding.nameInput.setEndIconOnClickListener {
                // Do not let the focus-loss listener persist the old value
                // after Reset has cleared it.
                binding.nameField.setOnFocusChangeListener(null)
                binding.nameField.clearFocus()
                binding.nameField.removeTextChangedListener(nameWatcher)
                binding.nameField.setText("")
                binding.nameField.addTextChangedListener(nameWatcher)
                viewModel.resetSimName(subId)
            }

            renderPalette(simColor)
        }

        private val nameWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                // Do not write to DataStore for every character. Each write
                // updates the adapter and can disturb the editor selection.
                // The value is committed on focus loss, IME Done, navigation,
                // or when this holder is recycled.
                binding.nameInput.isEndIconVisible =
                    SimDefaults.normalizeName(s?.toString()) != null
            }
        }

        fun commitName() {
            if (subId >= 0) normalizeDisplayedName()
        }

        private fun normalizeDisplayedName() {
            val rawName = binding.nameField.text?.toString()
            val normalized = SimDefaults.normalizeName(rawName)
            viewModel.setSimName(subId, normalized)
            if (rawName != normalized.orEmpty()) {
                binding.nameField.removeTextChangedListener(nameWatcher)
                binding.nameField.setText(normalized.orEmpty())
                binding.nameField.setSelection(binding.nameField.text?.length ?: 0)
                binding.nameField.addTextChangedListener(nameWatcher)
            }
        }

        private fun renderPalette(selectedColor: Int?) {
            binding.colorPalette.removeAllViews()
            val context = binding.root.context
            val colorNames = context.resources.getStringArray(R.array.settings_color_names)
            val dp = binding.root.resources.displayMetrics.density
            val ring = MaterialColors.getColor(binding.root, MaterialR.attr.colorOnSurface)
            SimDefaults.PALETTE.forEachIndexed { index, color ->
                val selected = color == selectedColor
                val colorName = colorNames.getOrElse(index) {
                    String.format("#%06X", 0xFFFFFF and color)
                }
                val swatch = ImageButton(context).apply {
                    // 48dp touch target around a 36dp swatch; the selected one
                    // gains an outer ring and a check mark.
                    layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt())
                    background = swatchDrawable(color, selected, ring, dp)
                    scaleType = android.widget.ImageView.ScaleType.CENTER
                    if (selected) {
                        setImageResource(R.drawable.ic_check)
                        val onSwatch = if (ColorUtils.calculateLuminance(color) > 0.5) {
                            android.graphics.Color.BLACK
                        } else {
                            android.graphics.Color.WHITE
                        }
                        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(onSwatch))
                    } else {
                        setImageDrawable(null)
                    }
                    isClickable = true
                    isFocusable = true
                    isSelected = selected
                    contentDescription = context.getString(
                        if (selected) R.string.settings_color_option_selected
                        else R.string.settings_color_option,
                        colorName
                    )
                    setOnClickListener {
                        viewModel.setSimColor(subId, color)
                        renderPalette(color)
                    }
                }
                binding.colorPalette.addView(swatch)
            }
        }

        private fun swatchDrawable(color: Int, selected: Boolean, ring: Int, dp: Float): android.graphics.drawable.Drawable {
            val fill = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            val layers = if (selected) {
                val outer = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke((2 * dp).toInt(), ring)
                }
                LayerDrawable(arrayOf(outer, fill)).apply {
                    setLayerInset(0, (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                    setLayerInset(1, (9 * dp).toInt(), (9 * dp).toInt(), (9 * dp).toInt(), (9 * dp).toInt())
                }
            } else {
                LayerDrawable(arrayOf(fill)).apply {
                    setLayerInset(0, (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                }
            }
            val ripple = MaterialColors.getColor(binding.root, MaterialR.attr.colorControlHighlight)
            return RippleDrawable(ColorStateList.valueOf(ripple), layers, null)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SimItem>() {
            override fun areItemsTheSame(oldItem: SimItem, newItem: SimItem): Boolean =
                oldItem.sub.subscriptionId == newItem.sub.subscriptionId

            override fun areContentsTheSame(oldItem: SimItem, newItem: SimItem): Boolean =
                oldItem == newItem
        }
    }
}
