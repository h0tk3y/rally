import android.Manifest
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.permissions.PermissionChecker
import com.h0tk3y.rally.android.permissions.PermissionHistory
import com.h0tk3y.rally.android.permissions.TelemetryAccessIssue
import com.h0tk3y.rally.android.permissions.TelemetryAccessPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The permission states follow what Android reports after the user's choices in the system dialog */
class TelemetryAccessPolicyTest {
    private val fine = Manifest.permission.ACCESS_FINE_LOCATION
    private val coarse = Manifest.permission.ACCESS_COARSE_LOCATION

    private class FakeChecker : PermissionChecker {
        val granted = mutableSetOf<String>()
        val rationale = mutableSetOf<String>()
        var gps = true
        override fun isGranted(permission: String) = permission in granted
        override fun shouldShowRationale(permission: String) = permission in rationale
        override fun hasGpsHardware() = gps
    }

    private val checker = FakeChecker()
    private val history = PermissionHistory()

    private fun issue() = TelemetryAccessPolicy.currentIssue(TelemetrySource.GPS, checker, history)

    private fun result(coarseBefore: Boolean = false) =
        TelemetryAccessPolicy.recordResult(TelemetrySource.GPS, coarseBefore, checker, history)

    /** "Don't allow": Android suggests a rationale after the first denial, and stops suggesting it after the second one */
    private fun deny(times: Int) {
        checker.rationale += listOf(fine, coarse)
        result()
        if (times == 2) {
            checker.rationale.clear()
            result()
        }
    }

    @Test
    fun `first denial is not permanent`() {
        deny(times = 1)
        assertEquals(TelemetryAccessIssue.PermissionDenied(TelemetrySource.GPS), issue())
    }

    @Test
    fun `second denial is permanent`() {
        deny(times = 2)
        assertEquals(TelemetryAccessIssue.PermissionPermanentlyDenied(TelemetrySource.GPS), issue())
    }

    @Test
    fun `dismissing the first dialog with back is not permanent`() {
        result() // no grant, no rationale
        assertEquals(TelemetryAccessIssue.PermissionDenied(TelemetrySource.GPS), issue())
    }

    @Test
    fun `no dialog twice in a row is permanent`() {
        // E.g. the denial was made permanent outside of the app, so the system answers with no dialog
        result()
        result()
        assertEquals(TelemetryAccessIssue.PermissionPermanentlyDenied(TelemetrySource.GPS), issue())
    }

    @Test
    fun `picking the approximate location on the first request is not permanent`() {
        checker.granted += coarse
        result(coarseBefore = false)
        assertEquals(TelemetryAccessIssue.ApproximateLocationOnly, issue())
    }

    @Test
    fun `approximate location after a denial is not permanent`() {
        deny(times = 1)
        checker.rationale.clear()
        checker.granted += coarse
        result(coarseBefore = false)
        assertEquals(TelemetryAccessIssue.ApproximateLocationOnly, issue())
    }

    @Test
    fun `declining the upgrade to precise location twice with no dialog is permanent`() {
        checker.granted += coarse
        result(coarseBefore = false)
        result(coarseBefore = true)
        result(coarseBefore = true)
        assertEquals(TelemetryAccessIssue.PermissionPermanentlyDenied(TelemetrySource.GPS), issue())
    }

    @Test
    fun `granting in the settings clears a permanent denial`() {
        deny(times = 2)
        checker.granted += listOf(fine, coarse)
        TelemetryAccessPolicy.forgetGranted(checker, history)
        assertNull(issue())

        checker.granted.clear()
        result()
        assertEquals("A later revoke starts over", TelemetryAccessIssue.PermissionDenied(TelemetrySource.GPS), issue())
    }

    @Test
    fun `no gps hardware is reported before the permissions`() {
        checker.gps = false
        assertEquals(TelemetryAccessIssue.NoGpsHardware, issue())
    }
}
