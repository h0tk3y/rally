package com.h0tk3y.rally.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.h0tk3y.rally.android.permissions.defaultTelemetrySource
import com.h0tk3y.rally.android.scenes.TimeAllowance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.dataStore by preferencesDataStore(
    name = "rally-prefs"
)

internal fun Context.preferenceRepository() = PreferenceRepository(dataStore, defaultTelemetrySource())

private object PreferencesKeys {
    val ALLOWANCE = stringPreferencesKey("allowance")
    val CALIBRATION = doublePreferencesKey("calibration")
    val TELEMETRY_SOURCE = stringPreferencesKey("telemetrySource")
    val BT_MAC = stringPreferencesKey("btMac")
    val SPEED_LIMIT_PERCENT_TEXT = stringPreferencesKey("speedLimitPercentText")
    val SEND_TELE_TO_IP = stringPreferencesKey("sendTeleToIp")
    val SOUND_FEEDBACK = booleanPreferencesKey("soundFeedback")
    val OUTGAUGE_PORT = intPreferencesKey("outgaugePort")
    val HUD_SWAP_PANELS = booleanPreferencesKey("hudSwapPanels")
    val BATTERY_HINT_SHOWN = booleanPreferencesKey("batteryHintShown")
    val SPEED_BUTTONS = stringPreferencesKey("speedButtons")
}

data class UserPreferences(
    val allowance: TimeAllowance?,
    val calibration: Double,
    val telemetrySource: TelemetrySource,
    val btMac: String?,
    val speedLimitPercent: String?,
    val sendTeleToIp: String?,
    val soundFeedback: Boolean,
    val outgaugePort: Int = 4444,
    val hudSwapPanels: Boolean = false,
    val batteryHintShown: Boolean = false,
    /** Never empty: a missing or unreadable stored value gives [SpeedButtons.DEFAULT] */
    val speedButtons: List<Int> = SpeedButtons.DEFAULT
)

enum class TelemetrySource {
    BT_OBD, GPS, SIMULATION, OUTGAUGE
}

/**
 * @param defaultTelemetrySource the source used until the user picks one; see [com.h0tk3y.rally.android.permissions.defaultTelemetrySource]
 */
class PreferenceRepository(
    val dataStore: DataStore<Preferences>,
    private val defaultTelemetrySource: TelemetrySource = TelemetrySource.GPS
) {
    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data.map { preferences ->
        mapUserPreferences(preferences, defaultTelemetrySource)
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
    
    suspend fun saveSendTeleToIp(value: String?) {
        dataStore.edit { preferences -> 
            preferences[PreferencesKeys.SEND_TELE_TO_IP] = value ?: ""
        }
    }

    suspend fun saveSoundFeedback(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOUND_FEEDBACK] = value
        }
    }

    suspend fun saveOutgaugePort(port: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.OUTGAUGE_PORT] = port
        }
    }

    suspend fun saveHudSwapPanels(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HUD_SWAP_PANELS] = value
        }
    }

    /** Saves only a list that [parseSpeedButtonsInput] accepts; anything else is ignored rather than stored */
    suspend fun saveSpeedButtons(speeds: List<Int>) {
        val encoded = encodeSpeedButtons(speeds)
        if (decodeStoredSpeedButtons(encoded) != speeds) return
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SPEED_BUTTONS] = encoded
        }
    }

    suspend fun saveBatteryHintShown() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BATTERY_HINT_SHOWN] = true
        }
    }

}

private fun mapUserPreferences(preferences: Preferences, defaultTelemetrySource: TelemetrySource): UserPreferences {
    // Get the sort order from preferences and convert it to a [SortOrder] object
    val timeAllowance =
        preferences[PreferencesKeys.ALLOWANCE]?.let { pref -> TimeAllowance.entries.find { it.name == pref } }
    val calibration = preferences[PreferencesKeys.CALIBRATION] ?: 1.0
    val telemetrySource = preferences[PreferencesKeys.TELEMETRY_SOURCE]?.let { value  -> TelemetrySource.entries.find { it.name == value } } 
        ?: defaultTelemetrySource
    val btMac = preferences[PreferencesKeys.BT_MAC]
    val speedLimitPercent = preferences[PreferencesKeys.SPEED_LIMIT_PERCENT_TEXT]
    val sendTeleToIp = preferences[PreferencesKeys.SEND_TELE_TO_IP]
    val soundFeedback = preferences[PreferencesKeys.SOUND_FEEDBACK] ?: false
    val outgaugePort = preferences[PreferencesKeys.OUTGAUGE_PORT] ?: 4444
    val hudSwapPanels = preferences[PreferencesKeys.HUD_SWAP_PANELS] ?: false
    val batteryHintShown = preferences[PreferencesKeys.BATTERY_HINT_SHOWN] ?: false
    // A value of another type under this key (e.g. restored from an edited backup) throws on the cast, so fall back to the default too
    val speedButtons = decodeStoredSpeedButtons(runCatching { preferences[PreferencesKeys.SPEED_BUTTONS] }.getOrNull())
    return UserPreferences(
        timeAllowance,
        calibration,
        telemetrySource,
        btMac,
        speedLimitPercent,
        sendTeleToIp?.takeIf { it.isNotEmpty() },
        soundFeedback,
        outgaugePort,
        hudSwapPanels,
        batteryHintShown,
        speedButtons
    )
}