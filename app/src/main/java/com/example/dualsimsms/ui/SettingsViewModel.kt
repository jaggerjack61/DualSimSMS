package com.example.dualsimsms.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dualsimsms.SmsApp
import com.example.dualsimsms.data.SettingsRepository.AppSettings
import com.example.dualsimsms.model.SimProfile
import com.example.dualsimsms.util.SimDefaults
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    data class SettingsUiState(
        val subscriptions: List<SimProfile> = emptyList(),
        val settings: AppSettings = AppSettings(),
        val isLoading: Boolean = true
    )

    private val container = (application as SmsApp).container
    private val settingsRepo = container.settingsRepository
    private val simRepo = container.simRepository

    val state: StateFlow<SettingsUiState> =
        combine(simRepo.subscriptionChanges(), settingsRepo.settingsFlow()) { subs, settings ->
            SettingsUiState(
                subscriptions = subs,
                settings = settings,
                isLoading = false
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setSimName(subId: Int, name: String?) {
        val normalized = SimDefaults.normalizeName(name)
        viewModelScope.launch { settingsRepo.setSimName(subId, normalized) }
    }

    fun resetSimName(subId: Int) {
        viewModelScope.launch { settingsRepo.setSimName(subId, null) }
    }

    fun setSimColor(subId: Int, colorArgb: Int) {
        viewModelScope.launch { settingsRepo.setSimColor(subId, colorArgb) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application) }
        }
    }
}

