package com.h0tk3y.rally.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.h0tk3y.rally.android.scenes.TimeAllowance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.dataStore by preferencesDataStore(
    name = "rally-prefs"
)

private object PreferencesKeys {
    val ALLOWANCE = stringPreferencesKey("allowance")
    val CALIBRATION = doublePreferencesKey("calibration")
    val TELEMETRY_SOURCE = stringPreferencesKey("telemetrySource")
    val BT_MAC = stringPreferencesKey("btMac")
    val SPEED_LIMIT_PERCENT_TEXT = stringPreferencesKey("speedLimitPercentText")
}

data class UserPreferences(
    val allowance: TimeAllowance?,
    val calibration: Double,
    val telemetrySource: TelemetrySource,
    val btMac: String?,
    val speedLimitPercent: String?
)

enum class TelemetrySource {
    BT_OBD, SIMULATION
}

class PreferenceRepository(val dataStore: DataStore<Preferences>) {
    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data.map { preferences ->
        mapUserPreferences(preferences)
    }

    suspend fun saveTimeAllowance(timeAllowance: TimeAllowance?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALLOWANCE] = timeAllowance.toString()
        }
    }

    suspend fun saveCalibrationFactor(calibration: Double?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CALIBRATION] = calibration ?: 1.0
        }
    }

    suspend fun saveBtMac(btMac: String?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BT_MAC] = btMac ?: ""
        }
    }

    suspend fun saveSpeedLimitPercent(value: String?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SPEED_LIMIT_PERCENT_TEXT] = value ?: ""
        }
    }

    suspend fun saveTelemetrySource(telemetrySource: TelemetrySource) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TELEMETRY_SOURCE] = telemetrySource.name
        }
    }
}

private fun mapUserPreferences(preferences: Preferences): UserPreferences {
    // Get the sort order from preferences and convert it to a [SortOrder] object
    val timeAllowance =
        preferences[PreferencesKeys.ALLOWANCE]?.let { pref -> TimeAllowance.entries.find { it.name == pref } }
    val calibration = preferences[PreferencesKeys.CALIBRATION] ?: 1.0
    val telemetrySource = preferences[PreferencesKeys.TELEMETRY_SOURCE]?.let { value  -> TelemetrySource.entries.find { it.name == value } } 
        ?: TelemetrySource.BT_OBD
    val btMac = preferences[PreferencesKeys.BT_MAC]
    val speedLimitPercent = preferences[PreferencesKeys.SPEED_LIMIT_PERCENT_TEXT]
    return UserPreferences(timeAllowance, calibration, telemetrySource, btMac, speedLimitPercent)
}