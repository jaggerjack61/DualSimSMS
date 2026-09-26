package com.example.dualsimsms.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dualsimsms.R
import com.example.dualsimsms.SmsApp
import com.example.dualsimsms.data.SettingsRepository.AppSettings
import com.example.dualsimsms.model.Message
import com.example.dualsimsms.model.SendState
import com.example.dualsimsms.model.SimProfile
import com.example.dualsimsms.telephony.SmsSender
import com.example.dualsimsms.util.DraftLogic
import com.example.dualsimsms.util.SimDefaults
import com.example.dualsimsms.util.SmsCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the conversation screen: message bubbles, read marking, draft
 * auto-save and the explicit-SIM send flow.
 */
class ConversationViewModel(
    application: Application,
    private val threadId: Long,
    private val address: String,
    /** SIM the thread was last active on; replies default to it. */
    private val preferredSubId: Int? = null
) : AndroidViewModel(application) {

    data class ComposeState(
        val body: String = "",
        val selectedSubId: Int? = null,
        val sending: Boolean = false
    )

    private val container = (application as SmsApp).container
    private val settingsRepo = container.settingsRepository
    private val simRepo = container.simRepository
    private val smsRepo = container.smsRepository
    private val sender = SmsSender(application)

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val subscriptions: StateFlow<List<SimProfile>> = simRepo.subscriptionChanges()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val messages: StateFlow<List<Message>> = smsRepo.messagesForThread(threadId, address)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _compose = MutableStateFlow(ComposeState())
    val compose: StateFlow<ComposeState> = _compose

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar

    private var draftJob: Job? = null
    private var lastSavedBody: String? = null
    private var draftPrefilled = false

    init {
        viewModelScope.launch {
            smsRepo.draftBodyForThread(threadId).collect { draft ->
                if (!draftPrefilled) {
                    // Mark the provider value as already saved before updating
                    // the EditText; its TextWatcher must not save it again.
                    lastSavedBody = draft.takeIf { it.isNotBlank() }
                    if (draft.isNotBlank()) {
                        _compose.update { it.copy(body = draft) }
                    }
                    draftPrefilled = true
                }
            }
        }
        viewModelScope.launch {
            // Read the live SIM list, not the StateFlow's empty initial value.
            val (subs, settings) = combine(
                simRepo.subscriptionChanges(),
                settingsRepo.settingsFlow()
            ) { subs, settings -> subs to settings }.first()
            val preferred = subs.firstOrNull { it.subscriptionId == preferredSubId }
                ?: subs.firstOrNull { it.subscriptionId == settings.lastSimSubId }
                ?: subs.firstOrNull()
            preferred?.let { sub ->
                // Never override a SIM the user already picked.
                _compose.update { state ->
                    if (state.selectedSubId == null) state.copy(selectedSubId = sub.subscriptionId) else state
                }
            }
        }
        viewModelScope.launch { smsRepo.markThreadAsRead(threadId) }
    }

    fun onBodyChanged(text: String) {
        _compose.update { it.copy(body = text) }

        // Serialize provider writes. A cancelled ContentResolver call can still
        // finish, so the replacement waits for it before saving or deleting.
        val previousDraftJob = draftJob
        previousDraftJob?.cancel()
        if (DraftLogic.shouldDelete(text)) {
            lastSavedBody = null
            draftJob = if (threadId > 0) {
                viewModelScope.launch {
                    previousDraftJob?.join()
                    withContext(Dispatchers.IO) { smsRepo.deleteDraft(threadId) }
                }
            } else {
                null
            }
            return
        }
        draftJob = viewModelScope.launch {
            previousDraftJob?.join()
            delay(DraftLogic.DEBOUNCE_MILLIS)
            if (!DraftLogic.shouldSave(text, lastSavedBody)) return@launch
            if (threadId <= 0) return@launch
            withContext(Dispatchers.IO) {
                smsRepo.saveDraft(threadId, address, text, _compose.value.selectedSubId)
            }
            lastSavedBody = text
        }
    }

    fun selectSub(subId: Int) {
        _compose.update { it.copy(selectedSubId = subId) }
        viewModelScope.launch { settingsRepo.setLastSim(subId) }
    }

    fun consumeSnackbar() {
        _snackbar.value = null
    }

    fun send() {
        val app = getApplication<Application>()
        val compose = _compose.value
        val body = compose.body
        if (body.isBlank() || compose.sending) return

        if (!SmsCapabilities.hasSmsPermissions(app)) {
            _snackbar.value = app.getString(R.string.error_permissions_required)
            return
        }
        if (!SmsCapabilities.isDefaultSmsHandler(app)) {
            _snackbar.value = app.getString(R.string.error_not_default_handler)
            return
        }
        val subId = compose.selectedSubId
        if (subId == null) {
            _snackbar.value = app.getString(R.string.error_no_sim_selected)
            return
        }
        if (simRepo.getSubscription(subId) == null) {
            _snackbar.value = app.getString(R.string.error_missing_sim)
            return
        }

        viewModelScope.launch {
            _compose.update { it.copy(sending = true) }
            val runId = System.currentTimeMillis()
            val rowId = withContext(Dispatchers.IO) {
                smsRepo.insertOutgoing(address, body, subId)
            }
            if (rowId == null) {
                _compose.update { it.copy(sending = false) }
                _snackbar.value = app.getString(R.string.error_not_default_handler)
                return@launch
            }
            val state = withContext(Dispatchers.IO) {
                sender.send(address, body, subId, runId, rowId)
            }
            if (state == SendState.SENDING) {
                // Wait for an in-flight auto-save before deleting; otherwise
                // it can recreate the just-sent body as a stale draft.
                draftJob?.cancelAndJoin()
                withContext(Dispatchers.IO) { smsRepo.deleteDraft(threadId) }
                lastSavedBody = null
                _compose.update { it.copy(sending = false, body = "") }
            } else {
                withContext(Dispatchers.IO) { smsRepo.finalizeOutgoing(rowId, state) }
                _compose.update { it.copy(sending = false) }
                _snackbar.value = messageFor(state)
            }
        }
    }

    fun colorFor(subId: Int?): Int {
        if (subId == null) return SimDefaults.COLOR_UNKNOWN
        settings.value.sims[subId]?.colorArgb?.let { return it }
        val sub = subscriptions.value.firstOrNull { it.subscriptionId == subId }
        return sub?.let { SimDefaults.defaultColor(it.slotIndex) } ?: SimDefaults.COLOR_UNKNOWN
    }

    fun simLabel(subId: Int?): String? {
        if (subId == null) return null
        val sub = subscriptions.value.firstOrNull { it.subscriptionId == subId } ?: return null
        return settings.value.sims[subId]?.customName ?: SimDefaults.defaultName(sub.slotIndex)
    }

    private fun messageFor(state: SendState): String = when (state) {
        SendState.NO_SERVICE -> getApplication<Application>().getString(R.string.error_no_service)
        SendState.RADIO_OFF -> getApplication<Application>().getString(R.string.error_radio_off)
        SendState.NULL_PDU, SendState.FAILED -> getApplication<Application>().getString(R.string.error_send_failed)
        SendState.NOT_PERMITTED -> getApplication<Application>().getString(R.string.error_not_permitted)
        SendState.NOT_DEFAULT_HANDLER -> getApplication<Application>().getString(R.string.error_not_default_handler)
        SendState.MISSING_SIM -> getApplication<Application>().getString(R.string.error_missing_sim)
        SendState.SENDING, SendState.SENT -> ""
    }

    companion object {
        fun factory(threadId: Long, address: String, preferredSubId: Int? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConversationViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                    threadId, address, preferredSubId
                )
            }
        }
    }
}

