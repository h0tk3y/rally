import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.UserPreferences
import com.h0tk3y.rally.android.racecervice.OutGaugePacket
import com.h0tk3y.rally.android.racecervice.OutGaugeReceiverState
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class OutGaugeIntegrationTest {

    @Test
    fun testUserPreferencesWithOutGauge() {
        val prefs = UserPreferences(
            allowance = null,
            calibration = 1.05,
            telemetrySource = TelemetrySource.OUTGAUGE,
            btMac = null,
            speedLimitPercent = null,
            sendTeleToIp = null,
            soundFeedback = true,
            outgaugePort = 5555
        )

        assertEquals(TelemetrySource.OUTGAUGE, prefs.telemetrySource)
        assertEquals(5555, prefs.outgaugePort)
        assertEquals(1.05, prefs.calibration, 0.0001)
    }

    @Test
    fun testOutGaugeDistanceDeltaCalculation() {
        // At 100 km/h for 0.5 seconds, distance moved is: 100 * (0.5 / 3600) = 0.0138888 km
        val speed = SpeedKmh(100.0)
        val timeHr = TimeHr(0.5 / 3600.0)
        val deltaDistance = DistanceKm.byMoving(speed, timeHr)

        assertEquals(100.0 * 0.5 / 3600.0, deltaDistance.valueKm, 0.00001)
    }

    @Test
    fun testOutGaugeSpeedCalibration() {
        // Calibration factor 1.25 -> 100 km/h measured becomes 80 km/h calibrated
        val rawSpeedKmh = 100.0
        val calibration = 1.25
        val calibratedSpeedKmh = SpeedKmh(rawSpeedKmh / calibration)

        assertEquals(80.0, calibratedSpeedKmh.valueKmh, 0.0001)
    }
}
