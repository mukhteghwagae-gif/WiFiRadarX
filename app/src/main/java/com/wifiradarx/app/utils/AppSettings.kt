package com.wifiradarx.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wifiradarx_settings")

object AppSettings {

    val SCAN_INTERVAL_S = intPreferencesKey("scan_interval_s")
    val IDW_POWER = floatPreferencesKey("idw_power")
    val DEAD_ZONE_THRESHOLD = floatPreferencesKey("dead_zone_threshold")
    val ROGUE_AP_SENSITIVITY = intPreferencesKey("rogue_ap_sensitivity")
    val BACKGROUND_MONITORING = booleanPreferencesKey("background_monitoring")
    val ENVIRONMENT_PRESET = stringPreferencesKey("environment_preset")
    val AR_HEATMAP_ENABLED = booleanPreferencesKey("ar_heatmap")
    val AR_VOXELS_ENABLED = booleanPreferencesKey("ar_voxels")
    val AR_ARROW_ENABLED = booleanPreferencesKey("ar_arrow")
    val AR_THREATS_ENABLED = booleanPreferencesKey("ar_threats")
    val FIRST_LAUNCH = booleanPreferencesKey("first_launch")

    data class Settings(
        val scanIntervalS: Int = 5,
        val idwPower: Float = 2.0f,
        val deadZoneThreshold: Float = -75f,
        val rogueApSensitivity: Int = 65,
        val backgroundMonitoring: Boolean = false,
        val environmentPreset: String = "OFFICE",
        val arHeatmapEnabled: Boolean = true,
        val arVoxelsEnabled: Boolean = false,
        val arArrowEnabled: Boolean = true,
        val arThreatsEnabled: Boolean = true,
        val firstLaunch: Boolean = true
    )

    fun getSettingsFlow(context: Context): Flow<Settings> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                Settings(
                    scanIntervalS = prefs[SCAN_INTERVAL_S] ?: 5,
                    idwPower = prefs[IDW_POWER] ?: 2.0f,
                    deadZoneThreshold = prefs[DEAD_ZONE_THRESHOLD] ?: -75f,
                    rogueApSensitivity = prefs[ROGUE_AP_SENSITIVITY] ?: 65,
                    backgroundMonitoring = prefs[BACKGROUND_MONITORING] ?: false,
                    environmentPreset = prefs[ENVIRONMENT_PRESET] ?: "OFFICE",
                    arHeatmapEnabled = prefs[AR_HEATMAP_ENABLED] ?: true,
                    arVoxelsEnabled = prefs[AR_VOXELS_ENABLED] ?: false,
                    arArrowEnabled = prefs[AR_ARROW_ENABLED] ?: true,
                    arThreatsEnabled = prefs[AR_THREATS_ENABLED] ?: true,
                    firstLaunch = prefs[FIRST_LAUNCH] ?: true
                )
            }

    suspend fun setScanInterval(context: Context, value: Int) {
        context.dataStore.edit { it[SCAN_INTERVAL_S] = value }
    }

    suspend fun setIdwPower(context: Context, value: Float) {
        context.dataStore.edit { it[IDW_POWER] = value }
    }

    suspend fun setFirstLaunch(context: Context, value: Boolean) {
        context.dataStore.edit { it[FIRST_LAUNCH] = value }
    }

    suspend fun setBackgroundMonitoring(context: Context, value: Boolean) {
        context.dataStore.edit { it[BACKGROUND_MONITORING] = value }
    }

    suspend fun saveAll(context: Context, settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[SCAN_INTERVAL_S] = settings.scanIntervalS
            prefs[IDW_POWER] = settings.idwPower
            prefs[DEAD_ZONE_THRESHOLD] = settings.deadZoneThreshold
            prefs[ROGUE_AP_SENSITIVITY] = settings.rogueApSensitivity
            prefs[BACKGROUND_MONITORING] = settings.backgroundMonitoring
            prefs[ENVIRONMENT_PRESET] = settings.environmentPreset
            prefs[AR_HEATMAP_ENABLED] = settings.arHeatmapEnabled
            prefs[AR_VOXELS_ENABLED] = settings.arVoxelsEnabled
            prefs[AR_ARROW_ENABLED] = settings.arArrowEnabled
            prefs[AR_THREATS_ENABLED] = settings.arThreatsEnabled
        }
    }
}
