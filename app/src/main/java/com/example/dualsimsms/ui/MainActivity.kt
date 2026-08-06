package com.example.dualsimsms.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.dualsimsms.R
import com.example.dualsimsms.data.Folder
import com.example.dualsimsms.databinding.ActivityMainBinding
import com.example.dualsimsms.telephony.SmsNotificationHelper
import com.example.dualsimsms.util.SmsCapabilities
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mainViewModel.refreshCapabilities()
    }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mainViewModel.refreshCapabilities()
    }

    private var settingsFragment: SettingsFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.pager.adapter = SimPagerAdapter(this)
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Swiping switches the SIM; the selected folder stays put.
                if (binding.pager.isVisible) {
                    binding.tabs.getTabAt(position)?.let { tab ->
                        if (!tab.isSelected) binding.tabs.selectTab(tab)
                    }
                    mainViewModel.selectTab(position)
                }
            }
        })

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.open_drawer, R.string.close_drawer
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        applyInsets()

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.navInbox -> selectFolder(Folder.INBOX)
                R.id.navSent -> selectFolder(Folder.SENT)
                R.id.navDrafts -> selectFolder(Folder.DRAFTS)
                R.id.navSettings -> showSettings()
            }
            true
        }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (binding.pager.isVisible && binding.pager.currentItem != tab.position) {
                    binding.pager.setCurrentItem(tab.position, false)
                }
                mainViewModel.selectTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.fabCompose.setOnClickListener { handleComposeRequested() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    binding.settingsHost.isVisible -> showPager()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        lifecycleScope.launch {
            mainViewModel.tabs.collect { tabs ->
                val selected = mainViewModel.selectedTabIndex.value
                binding.tabs.removeAllTabs()
                tabs.forEachIndexed { index, tab ->
                    binding.tabs.addTab(binding.tabs.newTab().setText(tab.label), index == selected)
                }
                (binding.pager.adapter as? SimPagerAdapter)?.updateTabs(tabs)
                if (binding.pager.currentItem != selected) {
                    binding.pager.setCurrentItem(selected, false)
                }
            }
        }

        handleSendToIntent(intent)

        binding.navView.setCheckedItem(folderMenuId(mainViewModel.selectedFolder.value))
        binding.toolbar.title = folderTitle(mainViewModel.selectedFolder.value)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendToIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshCapabilities()
        SmsNotificationHelper.ensureChannels(this)
    }

    fun requestSmsPermissions() {
        permissionLauncher.launch(SmsCapabilities.requiredPermissions.toTypedArray())
    }

    fun requestSmsRole() {
        val intent = mainViewModel.requestSmsRole(this) ?: return
        roleLauncher.launch(intent)
    }

    private fun handleComposeRequested() {
        when {
            !mainViewModel.hasSmsPermissions.value -> requestSmsPermissions()
            !mainViewModel.isDefaultSmsHandler.value -> showRoleDialog()
            else -> ComposeActivity.start(this)
        }
    }

    private fun showRoleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.role_dialog_title)
            .setMessage(R.string.role_dialog_message)
            .setPositiveButton(R.string.role_dialog_positive) { _, _ -> requestSmsRole() }
            .setNegativeButton(R.string.role_dialog_negative, null)
            .show()
    }

    private fun selectFolder(folder: Folder) {
        showPager()
        mainViewModel.selectFolder(folder)
        binding.navView.setCheckedItem(folderMenuId(folder))
        binding.toolbar.title = folderTitle(folder)
    }

    private fun showPager() {
        binding.settingsHost.isVisible = false
        settingsFragment?.let { supportFragmentManager.beginTransaction().remove(it).commit() }
        settingsFragment = null
        binding.pager.isVisible = true
        binding.tabs.isVisible = true
        // The compose button is always visible; tapping it walks through
        // permission and default-SMS-role prompts when sending isn't ready.
        binding.fabCompose.isVisible = true
        binding.navView.setCheckedItem(folderMenuId(mainViewModel.selectedFolder.value))
        binding.toolbar.title = folderTitle(mainViewModel.selectedFolder.value)
    }

    private fun showSettings() {
        binding.pager.isVisible = false
        binding.tabs.isVisible = false
        binding.fabCompose.isVisible = false
        binding.settingsHost.isVisible = true
        binding.toolbar.title = getString(R.string.settings_title)
        binding.navView.setCheckedItem(R.id.navSettings)
        if (settingsFragment == null) {
            settingsFragment = SettingsFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.settingsHost, settingsFragment!!)
            .commit()
    }

    private fun folderMenuId(folder: Folder): Int = when (folder) {
        Folder.INBOX -> R.id.navInbox
        Folder.SENT -> R.id.navSent
        Folder.DRAFTS -> R.id.navDrafts
    }

    private fun folderTitle(folder: Folder): String = when (folder) {
        Folder.INBOX -> getString(R.string.nav_inbox)
        Folder.SENT -> getString(R.string.nav_sent)
        Folder.DRAFTS -> getString(R.string.nav_drafts)
    }

    private fun handleSendToIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SENDTO) return
        val number = intent.data?.schemeSpecificPart
        if (!number.isNullOrBlank()) {
            ComposeActivity.start(this, number)
        }
    }

    private fun applyInsets() {
        // Grow the app bar by the status-bar inset so the toolbar keeps its
        // full height instead of being squeezed behind the status bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        // The NavigationView (a Material ScrimInsetsFrameLayout) applies the
        // status-bar and navigation-bar insets itself when it has
        // fitsSystemWindows="true".
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabCompose) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom + 16.dp
            v.layoutParams = params
            insets
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
