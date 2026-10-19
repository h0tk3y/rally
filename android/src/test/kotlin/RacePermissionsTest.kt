import android.Manifest
import android.content.pm.ServiceInfo
import android.os.Build
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.permissions.racePermissions
import com.h0tk3y.rally.android.permissions.raceServiceType
import org.junit.Assert.assertEquals
import org.junit.Test

class RacePermissionsTest {
    private val fine = Manifest.permission.ACCESS_FINE_LOCATION
    private val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
    private val btConnect = Manifest.permission.BLUETOOTH_CONNECT
    private val notifications = Manifest.permission.POST_NOTIFICATIONS

    @Test
    fun `gps requires fine and coarse location together on all versions`() {
        for (sdk in listOf(Build.VERSION_CODES.O, Build.VERSION_CODES.R, Build.VERSION_CODES.S, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            assertEquals(listOf(fine, coarse), racePermissions(TelemetrySource.GPS, btDeviceSelected = true, sdk).required)
        }
    }

    @Test
    fun `obd requires bluetooth connect only on android 12+ with a selected adapter`() {
        assertEquals(listOf(btConnect), racePermissions(TelemetrySource.BT_OBD, btDeviceSelected = true, Build.VERSION_CODES.S).required)
        assertEquals(emptyList<String>(), racePermissions(TelemetrySource.BT_OBD, btDeviceSelected = false, Build.VERSION_CODES.S).required)
        assertEquals(emptyList<String>(), racePermissions(TelemetrySource.BT_OBD, btDeviceSelected = true, Build.VERSION_CODES.R).required)
    }

    @Test
    fun `simulation and outgauge require nothing`() {
        for (source in listOf(TelemetrySource.SIMULATION, TelemetrySource.OUTGAUGE)) {
            assertEquals(emptyList<String>(), racePermissions(source, btDeviceSelected = true, Build.VERSION_CODES.UPSIDE_DOWN_CAKE).required)
        }
    }

    @Test
    fun `notifications are optional and only requested on android 13+`() {
        assertEquals(listOf(notifications), racePermissions(TelemetrySource.GPS, true, Build.VERSION_CODES.TIRAMISU).optional)
        assertEquals(emptyList<String>(), racePermissions(TelemetrySource.GPS, true, Build.VERSION_CODES.S_V2).optional)
    }

    @Test
    fun `service uses the location type only for gps with fine location`() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, raceServiceType(TelemetrySource.GPS, fineLocationGranted = true))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, raceServiceType(TelemetrySource.GPS, fineLocationGranted = false))
        for (source in listOf(TelemetrySource.BT_OBD, TelemetrySource.SIMULATION, TelemetrySource.OUTGAUGE)) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, raceServiceType(source, fineLocationGranted = true))
        }
    }
}
