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
}

data class UserPreferences(val allowance: TimeAllowance?, val calibration: Double)

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
}

private fun mapUserPreferences(preferences: Preferences): UserPreferences {
    // Get the sort order from preferences and convert it to a [SortOrder] object
    val timeAllowance =
        preferences[PreferencesKeys.ALLOWANCE]?.let { pref -> TimeAllowance.values().find { it.name == pref } }
    val calibration = preferences[PreferencesKeys.CALIBRATION] ?: 1.0
    return UserPreferences(timeAllowance, calibration)
}