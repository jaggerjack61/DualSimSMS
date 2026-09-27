package com.example.dualsimsms.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dualsimsms.R
import com.example.dualsimsms.SmsApp
import com.example.dualsimsms.data.Folder
import com.example.dualsimsms.data.SettingsRepository.AppSettings
import com.example.dualsimsms.model.Conversation
import com.example.dualsimsms.model.SimProfile
import com.example.dualsimsms.util.ConversationGrouper
import com.example.dualsimsms.util.ConversationSearch
import com.example.dualsimsms.util.SimDefaults
import com.example.dualsimsms.util.SimFilter
import com.example.dualsimsms.util.SmsCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity-scoped state for the main screen: subscriptions, tabs, filters,
 * permissions and the conversation lists behind each folder.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as SmsApp).container
    private val settingsRepo = container.settingsRepository
    private val simRepo = container.simRepository
    private val contactRepo = container.contactRepository
    private val smsRepo = container.smsRepository

    private val _subscriptions = MutableStateFlow<List<SimProfile>>(emptyList())
    val subscriptions: StateFlow<List<SimProfile>> = _subscriptions

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _tabs = MutableStateFlow<List<TabModel>>(emptyList())
    val tabs: StateFlow<List<TabModel>> = _tabs

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex

    private val _selectedFolder = MutableStateFlow(Folder.INBOX)
    val selectedFolder: StateFlow<Folder> = _selectedFolder

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _hasSmsPermissions = MutableStateFlow(false)
    val hasSmsPermissions: StateFlow<Boolean> = _hasSmsPermissions

    private val _isDefaultSmsHandler = MutableStateFlow(false)
    val isDefaultSmsHandler: StateFlow<Boolean> = _isDefaultSmsHandler

    val currentTab: TabModel?
        get() = _tabs.value.getOrNull(_selectedTabIndex.value)

    init {
        viewModelScope.launch {
            combine(simRepo.subscriptionChanges(), settingsRepo.settingsFlow()) { subs, settings ->
                subs to settings
            }.collect { (subs, settings) ->
                _subscriptions.value = subs
                _settings.value = settings
                rebuildTabs(subs, settings)
            }
        }
        refreshCapabilities()
    }

    fun refreshCapabilities() {
        val app = getApplication<Application>()
        _hasSmsPermissions.value = SmsCapabilities.hasSmsPermissions(app)
        _isDefaultSmsHandler.value = SmsCapabilities.isDefaultSmsHandler(app)
    }

    fun requestSmsRole(context: Context): Intent? = SmsCapabilities.requestSmsRole(context)

    fun selectTab(index: Int) {
        if (index !in _tabs.value.indices) return
        _selectedTabIndex.value = index
        val subId = _tabs.value[index].subId ?: return
        viewModelScope.launch { settingsRepo.setLastSim(subId) }
    }

    fun selectFolder(folder: Folder) {
        _selectedFolder.value = folder
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Conversations restricted to [subId] (null = all SIMs) and narrowed by
     * the search query. Permission and folder are combined into one value so
     * an emission from the previous folder can never be rendered under a
     * newly selected drawer title. The query filters in memory, so typing
     * does not re-read the SMS provider or contacts.
     */
    fun conversationsFor(subId: Int?): Flow<UiState<List<Conversation>>> =
        combine(_hasSmsPermissions, _selectedFolder) { permission, folder ->
            permission to folder
        }.flatMapLatest { (hasPermission, folder) ->
            if (!hasPermission) {
                flowOf(UiState.Error)
            } else {
                val threads = smsRepo.messagesFor(folder).map { messages ->
                    val filtered = SimFilter.filter(messages, subId)
                    val conversations = withContext(Dispatchers.IO) {
                        ConversationGrouper.group(filtered).map { conversation ->
                            conversation.copy(
                                contactName = contactRepo.nameFor(conversation.address)
                            )
                        }
                    }
                    conversations to filtered
                }
                combine(threads, _searchQuery.map(String::trim).distinctUntilChanged()) {
                        (conversations, messages), query ->
                    val results = ConversationSearch.filter(conversations, messages, query)
                    if (results.isEmpty()) UiState.Empty else UiState.Success(results)
                }
            }
        }

    fun colorFor(subId: Int?): Int {
        if (subId == null) return SimDefaults.COLOR_UNKNOWN
        _settings.value.sims[subId]?.colorArgb?.let { return it }
        val sub = _subscriptions.value.firstOrNull { it.subscriptionId == subId }
        return sub?.let { SimDefaults.defaultColor(it.slotIndex) } ?: SimDefaults.COLOR_UNKNOWN
    }

    fun simLabel(subId: Int?): String? {
        if (subId == null) return null
        val sub = _subscriptions.value.firstOrNull { it.subscriptionId == subId } ?: return null
        return _settings.value.sims[subId]?.customName ?: SimDefaults.defaultName(sub.slotIndex)
    }

    private fun rebuildTabs(subs: List<SimProfile>, settings: AppSettings) {
        val lastSim = settings.lastSimSubId
        val tabs = buildList {
            add(TabModel(null, getApplication<Application>().getString(R.string.tab_all_sims)))
            subs.forEach { sub ->
                val custom = settings.sims[sub.subscriptionId]
                val label = custom?.customName ?: SimDefaults.defaultName(sub.slotIndex)
                val color = custom?.colorArgb ?: SimDefaults.defaultColor(sub.slotIndex)
                add(TabModel(sub.subscriptionId, label, color))
            }
        }
        _tabs.value = tabs
        if (_selectedTabIndex.value !in tabs.indices) {
            val preferred = tabs.indexOfFirst { it.subId == lastSim }.let { if (it < 0) 0 else it }
            _selectedTabIndex.value = preferred
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application) }
        }
    }
}

