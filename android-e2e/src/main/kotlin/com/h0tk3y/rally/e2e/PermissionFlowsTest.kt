package com.h0tk3y.rally.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.h0tk3y.rally.e2e.RallyApp.Companion.BLUETOOTH
import com.h0tk3y.rally.e2e.RallyApp.Companion.BLUETOOTH_CONNECT
import com.h0tk3y.rally.e2e.RallyApp.Companion.COARSE_LOCATION
import com.h0tk3y.rally.e2e.RallyApp.Companion.FINE_LOCATION
import com.h0tk3y.rally.e2e.RallyApp.Companion.GPS_APPROXIMATE
import com.h0tk3y.rally.e2e.RallyApp.Companion.GPS_NO_PERMISSION
import com.h0tk3y.rally.e2e.RallyApp.Companion.GPS_REASON
import com.h0tk3y.rally.e2e.RallyApp.Companion.GPS_TITLE
import com.h0tk3y.rally.e2e.RallyApp.Companion.LOCATION
import com.h0tk3y.rally.e2e.RallyApp.Companion.OBD_REASON
import com.h0tk3y.rally.e2e.RallyApp.Companion.OBD_TITLE
import com.h0tk3y.rally.e2e.RallyApp.Companion.TYPE_CONNECTED_DEVICE
import com.h0tk3y.rally.e2e.RallyApp.Companion.TYPE_LOCATION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The permission flows of the telemetry sources, as the user goes through them.
 * The default telemetry source of an emulator is GPS, since it has GPS hardware.
 */
@RunWith(AndroidJUnit4::class)
class PermissionFlowsTest {
    private val app = RallyApp()

    @Before
    fun setUp() {
        app.resetAndLaunch()
        app.createSection()
    }

    @Test
    fun gpsDeniedThenExplainedThenGrantedPrecise() {
        app.tapRaceMode()
        app.systemDeny()

        // After a denial, the app explains why it needs the permission rather than crashing or doing nothing
        app.waitForExplanation(GPS_TITLE, GPS_REASON)
        app.explanationAllow()

        app.waitFor(By.text("Precise"))
        app.waitFor(By.text("Approximate"))
        app.systemAllowLocation(precise = true)

        app.waitForRaceMode()
        assertTrue(app.isGranted(FINE_LOCATION))
        app.waitForRaceServiceTypes(TYPE_LOCATION)
    }

    @Test
    fun approximateLocationOnlyIsExplained() {
        app.tapRaceMode()
        app.systemDeny()
        app.waitForExplanation(GPS_TITLE, GPS_REASON)
        app.explanationAllow()
        app.systemAllowLocation(precise = false)

        app.waitForExplanation(GPS_TITLE, GPS_APPROXIMATE)
        assertTrue(app.isGranted(COARSE_LOCATION))
        assertFalse(app.isGranted(FINE_LOCATION))
        assertFalse(app.has(By.text("Race not started")))

        app.explanationAllow()
        app.systemUpgradeToPrecise()
        app.waitForRaceMode()
        app.waitForRaceServiceTypes(TYPE_LOCATION)
    }

    /**
     * Android suggests no rationale for the fine location right after the user picks the approximate one, so the app takes it
     * for a permanent denial and offers the app settings, even though the system would still show the dialog to upgrade
     * to the precise location.
     */
    @Ignore("Bug: the approximate location picked on the first request is treated as a permanent denial")
    @Test
    fun approximateLocationOnTheFirstRequestIsExplained() {
        app.tapRaceMode()
        app.systemAllowLocation(precise = false)

        app.waitForExplanation(GPS_TITLE, GPS_APPROXIMATE)
        app.explanationAllow()
        app.systemUpgradeToPrecise()
        app.waitForRaceMode()
    }

    @Test
    fun revokingLocationStopsTheServiceForGood() {
        app.tapRaceMode()
        app.systemAllowLocation(precise = true)
        app.waitForRaceMode()
        app.waitForRaceServiceTypes(TYPE_LOCATION)
        assertTrue(app.pid().isNotEmpty())

        app.revoke(*LOCATION)

        // Android kills the app on a revoke; a sticky service would be scheduled for a restart and then crash on startForeground
        waitUntil("the app process dies") { app.pid().isEmpty() }
        repeat(10) {
            Thread.sleep(500)
            assertEquals("The app must not be restarted", "", app.pid())
            assertNull("The race service must not be restarted", app.raceServiceTypes())
        }

        app.launch()
        app.openSection()
        app.tapRaceMode()
        app.waitForExplanation(GPS_TITLE, GPS_REASON)
    }

    @Test
    fun obdPermissionIsRequestedInSettingsAndOnRaceMode() {
        app.back() // to the section list
        app.openSettings()
        app.click("OBD over Bluetooth")

        // Picking the OBD source with no adapter set up yet asks for the permission right away
        app.systemDeny()
        app.waitForExplanation(OBD_TITLE, OBD_REASON)
        app.explanationNotNow()

        app.back()
        app.openSection()
        app.tapRaceMode()
        app.waitForExplanation(OBD_TITLE, OBD_REASON)
        app.explanationAllow()
        app.systemAllow()

        app.waitForRaceMode()
        assertTrue(app.isGranted(BLUETOOTH_CONNECT))
        app.waitForRaceServiceTypes(TYPE_CONNECTED_DEVICE)
        app.waitForText("OBD: no MAC!")
    }

    @Test
    fun grantWhileTheServiceRunsSwitchesItToLocation() {
        // Enter the race mode with a source that needs no permissions, then switch to GPS without granting the location
        app.back()
        app.openSettings()
        app.click("Simulation")
        app.back()
        app.openSection()
        app.tapRaceMode()
        app.waitForRaceMode()
        app.waitForText("Simulation ✔")

        app.openSettings()
        app.click("GPS")
        app.systemDeny()
        app.waitForExplanation(GPS_TITLE, GPS_REASON)
        app.explanationNotNow()
        app.back()

        // The missing permission is explained once when the race panel shows it
        app.waitForExplanation(GPS_TITLE, GPS_REASON)
        app.explanationNotNow()
        app.waitForText(GPS_NO_PERMISSION)
        app.waitForRaceServiceTypes(TYPE_CONNECTED_DEVICE)

        // E.g. granted in the system settings; a tap on the status picks it up
        app.grant(*LOCATION)
        app.click(GPS_NO_PERMISSION)

        app.waitForRaceServiceTypes(TYPE_LOCATION)
        app.waitGone(By.text(GPS_NO_PERMISSION))
        assertFalse(app.has(By.text(GPS_TITLE)))
    }

    @Test
    fun typedMacIsKeptIntact() {
        app.grant(*BLUETOOTH) // not to be interrupted by the permission request
        app.back()
        app.openSettings()
        app.click("OBD over Bluetooth")

        val mac = "00:1D:A5:68:98:8B"
        val field = app.waitFor(By.clazz("android.widget.EditText").hasDescendant(By.text("Bluetooth MAC address")))
        field.click()
        // Typed character by character, as the saved value lags behind the typing and used to reset the field
        app.shell("input text $mac")
        waitUntil("the field shows the typed MAC") { field.text == mac }
        Thread.sleep(1000)
        assertEquals(mac, field.text)

        app.back() // hides the keyboard
        if (app.has(By.text("Telemetry source"))) app.back()
        app.openSettings()
        app.waitForText(mac)
    }

    private fun waitUntil(what: String, timeoutMs: Long = RallyApp.TIMEOUT, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting until $what")
            Thread.sleep(200)
        }
    }
}
