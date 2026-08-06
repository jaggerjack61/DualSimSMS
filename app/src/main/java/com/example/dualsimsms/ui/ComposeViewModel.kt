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
import com.example.dualsimsms.data.SmsStore
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the new-message compose screen: recipient suggestions, SIM
 * selection, draft auto-save and sending.
 */
class ComposeViewModel(application: Application) : AndroidViewModel(application) {

    data class SentDestination(
        val threadId: Long,
        val address: String,
        val subId: Int
    )

    data class ComposeUiState(
        val subscriptions: List<SimProfile> = emptyList(),
        val settings: AppSettings = AppSettings(),
        val selectedSubId: Int? = null,
        val sending: Boolean = false,
        val message: String? = null
    )

    private val container = (application as SmsApp).container
    private val settingsRepo = container.settingsRepository
    private val simRepo = container.simRepository
    private val smsRepo = container.smsRepository
    private val sender = SmsSender(application)

    private val _state = MutableStateFlow(ComposeUiState())
    val state: StateFlow<ComposeUiState> = _state

    private val _sent = MutableSharedFlow<SentDestination>(extraBufferCapacity = 1)
    val sent: SharedFlow<SentDestination> = _sent

    private var draftJob: Job? = null
    private var lastSavedBody: String? = null
    private var lastDraftThreadId: Long? = null

    init {
        viewModelScope.launch {
            combine(simRepo.subscriptionChanges(), settingsRepo.settingsFlow()) { subs, settings ->
                subs to settings
            }.collect { (subs, settings) ->
                val current = _state.value
                val validSubIds = subs.map { it.subscriptionId }
                val selected = current.selectedSubId?.takeIf { it in validSubIds }
                    ?: subs.firstOrNull { it.subscriptionId == settings.lastSimSubId }?.subscriptionId
                    ?: subs.firstOrNull()?.subscriptionId
                _state.update { it.copy(subscriptions = subs, settings = settings, selectedSubId = selected) }
            }
        }
    }

    fun selectSub(subId: Int) {
        _state.update { it.copy(selectedSubId = subId) }
        viewModelScope.launch { settingsRepo.setLastSim(subId) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun onTextChanged(recipient: String, body: String) {
        val previousDraftJob = draftJob
        previousDraftJob?.cancel()

        if (DraftLogic.shouldDelete(body) || recipient.isBlank()) {
            val knownThreadId = lastDraftThreadId
            lastSavedBody = null
            lastDraftThreadId = null
            // A blank, brand-new compose screen must not erase a draft that
            // belongs to the selected recipient in another screen. Delete only
            // a draft created by this ComposeViewModel instance.
            draftJob = knownThreadId?.let { threadId ->
                viewModelScope.launch {
                    previousDraftJob?.join()
                    withContext(Dispatchers.IO) { smsRepo.deleteDraft(threadId) }
                }
            }
            return
        }

        draftJob = viewModelScope.launch {
            previousDraftJob?.join()
            delay(DraftLogic.DEBOUNCE_MILLIS)
            val threadId = withContext(Dispatchers.IO) {
                SmsStore.resolveThreadId(getApplication(), recipient)
            }
            if (threadId <= 0) return@launch
            if (threadId == lastDraftThreadId &&
                !DraftLogic.shouldSave(body, lastSavedBody)
            ) return@launch

            val oldThreadId = lastDraftThreadId
            lastDraftThreadId = threadId
            withContext(Dispatchers.IO) {
                if (oldThreadId != null && oldThreadId != threadId) {
                    smsRepo.deleteDraft(oldThreadId)
                }
                smsRepo.saveDraft(threadId, recipient, body, _state.value.selectedSubId)
            }
            lastSavedBody = body
        }
    }

    fun send(recipient: String, body: String) {
        val app = getApplication<Application>()
        val state = _state.value
        if (recipient.isBlank() || body.isBlank() || state.sending) return

        if (!SmsCapabilities.hasSmsPermissions(app)) {
            _state.update { it.copy(message = app.getString(R.string.error_permissions_required)) }
            return
        }
        if (!SmsCapabilities.isDefaultSmsHandler(app)) {
            _state.update { it.copy(message = app.getString(R.string.error_not_default_handler)) }
            return
        }
        val subId = state.selectedSubId
        if (subId == null) {
            _state.update { it.copy(message = app.getString(R.string.error_no_sim_selected)) }
            return
        }
        if (simRepo.getSubscription(subId) == null) {
            _state.update { it.copy(message = app.getString(R.string.error_missing_sim)) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            val runId = System.currentTimeMillis()
            val rowId = withContext(Dispatchers.IO) {
                smsRepo.insertOutgoing(recipient, body, subId)
            }
            if (rowId == null) {
                _state.update { it.copy(sending = false, message = app.getString(R.string.error_not_default_handler)) }
                return@launch
            }
            val sendState = withContext(Dispatchers.IO) {
                sender.send(recipient, body, subId, runId, rowId)
            }
            val threadId = withContext(Dispatchers.IO) {
                SmsStore.resolveThreadId(app, recipient)
            }
            _state.update { it.copy(sending = false) }
            if (sendState == SendState.SENDING) {
                // A cancelled ContentResolver write may finish normally. Join
                // it before deleting so it cannot recreate the sent draft.
                draftJob?.cancelAndJoin()
                withContext(Dispatchers.IO) {
                    smsRepo.deleteDraft(threadId)
                    lastDraftThreadId?.takeIf { it != threadId }?.let { smsRepo.deleteDraft(it) }
                }
                lastSavedBody = null
                lastDraftThreadId = null
                _sent.emit(SentDestination(threadId, recipient, subId))
            } else {
                withContext(Dispatchers.IO) { smsRepo.finalizeOutgoing(rowId, sendState) }
                _state.update { it.copy(message = sendMessage(sendState)) }
            }
        }
    }

    fun colorFor(subId: Int?): Int {
        if (subId == null) return SimDefaults.COLOR_UNKNOWN
        val settings = _state.value.settings
        settings.sims[subId]?.colorArgb?.let { return it }
        val sub = _state.value.subscriptions.firstOrNull { it.subscriptionId == subId }
        return sub?.let { SimDefaults.defaultColor(it.slotIndex) } ?: SimDefaults.COLOR_UNKNOWN
    }

    fun simLabel(subId: Int?): String? {
        if (subId == null) return null
        val sub = _state.value.subscriptions.firstOrNull { it.subscriptionId == subId } ?: return null
        return _state.value.settings.sims[subId]?.customName ?: SimDefaults.defaultName(sub.slotIndex)
    }

    private fun sendMessage(state: SendState): String = when (state) {
        SendState.NO_SERVICE -> getApplication<Application>().getString(R.string.error_no_service)
        SendState.RADIO_OFF -> getApplication<Application>().getString(R.string.error_radio_off)
        SendState.NULL_PDU, SendState.FAILED -> getApplication<Application>().getString(R.string.error_send_failed)
        SendState.NOT_PERMITTED -> getApplication<Application>().getString(R.string.error_not_permitted)
        SendState.NOT_DEFAULT_HANDLER -> getApplication<Application>().getString(R.string.error_not_default_handler)
        SendState.MISSING_SIM -> getApplication<Application>().getString(R.string.error_missing_sim)
        SendState.SENDING, SendState.SENT -> ""
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ComposeViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application) }
        }
    }
}

