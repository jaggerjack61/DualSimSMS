package com.example.dualsimsms.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "sim_settings")

/**
 * Persists the small amount of app-owned state: custom SIM names, SIM colors
 * and the last-selected SIM subscription id.
 */
class SettingsRepository(private val context: Context) {

    data class SimSettings(
        val customName: String? = null,
        val colorArgb: Int? = null
    )

    data class AppSettings(
        val sims: Map<Int, SimSettings> = emptyMap(),
        val lastSimSubId: Int? = null
    )

    private val simNameKey = { subId: Int -> stringPreferencesKey("sim_name_$subId") }
    private val simColorKey = { subId: Int -> intPreferencesKey("sim_color_$subId") }
    private val lastSimKey = intPreferencesKey("last_sim_sub_id")

    fun settingsFlow(): Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val names = prefs.asMap().entries.mapNotNull { entry ->
            val subId = entry.key.name.removePrefix("sim_name_").toIntOrNull() ?: return@mapNotNull null
            val name = (entry.value as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            subId to name
        }.toMap()
        val colors = prefs.asMap().entries.mapNotNull { entry ->
            val subId = entry.key.name.removePrefix("sim_color_").toIntOrNull() ?: return@mapNotNull null
            val color = entry.value as? Int ?: return@mapNotNull null
            subId to color
        }.toMap()
        AppSettings(
            sims = (names.keys + colors.keys).associateWith { subId ->
                SimSettings(
                    customName = names[subId],
                    colorArgb = colors[subId]
                )
            },
            lastSimSubId = prefs[lastSimKey]
        )
    }

    suspend fun setSimName(subId: Int, name: String?) {
        val normalized = name?.trim()?.takeIf { it.isNotEmpty() }
        context.settingsDataStore.edit { prefs ->
            if (normalized == null) {
                prefs.remove(simNameKey(subId))
            } else {
                prefs[simNameKey(subId)] = normalized
            }
        }
    }

    suspend fun setSimColor(subId: Int, colorArgb: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[simColorKey(subId)] = colorArgb
        }
    }

    suspend fun setLastSim(subId: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastSimKey] = subId
        }
    }
}
