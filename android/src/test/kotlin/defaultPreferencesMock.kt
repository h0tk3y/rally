import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.UserPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

fun defaultPreferencesMock(): PreferenceRepository {
    return mockk<PreferenceRepository>() {
        every { userPreferencesFlow }.returns(
            flowOf(
                UserPreferences(
                    allowance = null,
                    calibration = 1.0,
                    telemetrySource = TelemetrySource.SIMULATION,
                    btMac = null,
                    speedLimitPercent = null,
                    sendTeleToIp = null,
                    soundFeedback = false
                )
            )
        )
    }
}
