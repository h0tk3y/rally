package com.h0tk3y.rally.e2e

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail

/** Drives the app under test from the outside, with UiAutomator and shell commands */
class RallyApp {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)

    /** Starts from a clean state: no sections, default settings, and no permissions but the notifications */
    fun resetAndLaunch() {
        // Clears the data and resets the runtime permissions, including the "don't ask again" state
        shell("pm clear $PACKAGE")
        revoke(*LOCATION, *BLUETOOTH)
        shell("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
        // Otherwise, the first entry into the race mode suggests turning the battery optimization off
        shell("cmd deviceidle whitelist +$PACKAGE")
        device.pressHome()
        launch()
    }

    fun launch() {
        shell("am start -W -n $PACKAGE/$ACTIVITY")
        waitFor(By.pkg(PACKAGE).depth(0))
    }

    fun shell(command: String): String {
        val output = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
    }

    /** Bluetooth permissions are in the same group, so a single one left granted would get the others auto-granted on a request */
    fun revoke(vararg permissions: String) = permissions.forEach { shell("pm revoke $PACKAGE $it") }

    fun grant(vararg permissions: String) = permissions.forEach { shell("pm grant $PACKAGE $it") }

    fun isGranted(permission: String): Boolean =
        shell("dumpsys package $PACKAGE").lineSequence().any { it.trim().startsWith("$permission: granted=true") }

    fun pid(): String = shell("pidof $PACKAGE").trim()

    /** The foreground service types of the running race service, or null if it is not running */
    fun raceServiceTypes(): Int? {
        val dump = shell("dumpsys activity services $PACKAGE/$RACE_SERVICE")
        if (!dump.contains("ServiceRecord")) return null
        val types = Regex("types=0x([0-9a-fA-F]+)").find(dump) ?: return 0
        return types.groupValues[1].toInt(16)
    }

    fun waitForRaceServiceTypes(expected: Int, timeoutMs: Long = TIMEOUT) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var actual: Int?
        do {
            actual = raceServiceTypes()
            if (actual == expected) return
            Thread.sleep(200)
        } while (System.currentTimeMillis() < deadline)
        fail("Expected the race service to run with types=0x${expected.toString(16)}, but got ${actual?.let { "0x" + it.toString(16) } ?: "no service"}")
    }

    fun waitFor(selector: BySelector, timeoutMs: Long = TIMEOUT): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs).also { assertNotNull("Not found: $selector", it) }

    fun waitForText(text: String) = waitFor(By.text(text))

    fun waitGone(selector: BySelector, timeoutMs: Long = TIMEOUT) {
        if (!device.wait(Until.gone(selector), timeoutMs)) fail("Still shown: $selector")
    }

    fun click(text: String) = click(By.text(text))

    /** Retries when the object is replaced while clicking, e.g. during a screen transition */
    fun click(selector: BySelector) {
        repeat(5) {
            try {
                waitFor(selector).click()
                return
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
        waitFor(selector).click()
    }

    fun has(selector: BySelector) = device.hasObject(selector)

    // The app screens

    /** Creates an empty section from the section list and opens it */
    fun createSection(name: String = "e2e") {
        click(By.desc("Create section"))
        waitForText("New section name")
        shell("input text $name")
        click("Save")
        waitForText(RACE_MODE)
    }

    fun openSection(name: String = "e2e") {
        if (device.wait(Until.hasObject(By.text(RACE_MODE)), 2000)) return
        click(name)
        waitForText(RACE_MODE)
    }

    fun tapRaceMode() = click(RACE_MODE)

    fun openSettings() {
        click(By.desc("Show menu"))
        click("Settings")
        waitForText("Telemetry source")
    }

    fun back() {
        device.pressBack()
        device.waitForIdle()
    }

    /** The race panel is shown, so the race mode is on */
    fun waitForRaceMode() = waitForText("Race not started")

    // The explanation dialog of the app

    fun waitForExplanation(title: String, text: String): UiObject2 {
        waitForText(title)
        return waitFor(By.textContains(text))
    }

    fun explanationAllow() = click("Allow")

    fun explanationNotNow() {
        click("Not now")
        waitGone(By.text("Not now"))
    }

    // The system permission dialog

    fun waitForSystemDialog(): UiObject2 = waitFor(By.res(PERMISSION_CONTROLLER, "grant_dialog"))

    fun systemDeny() {
        waitForSystemDialog()
        clickSystem("permission_deny_button")
        waitGone(By.res(PERMISSION_CONTROLLER, "grant_dialog"))
    }

    /** Grants the location permissions in the system dialog, with the precise or approximate accuracy */
    fun systemAllowLocation(precise: Boolean) {
        waitForSystemDialog()
        clickSystem(if (precise) "permission_location_accuracy_radio_fine" else "permission_location_accuracy_radio_coarse")
        clickSystem("permission_allow_foreground_only_button")
        waitGone(By.res(PERMISSION_CONTROLLER, "grant_dialog"))
    }

    fun systemAllow() {
        waitForSystemDialog()
        clickSystem("permission_allow_button")
        waitGone(By.res(PERMISSION_CONTROLLER, "grant_dialog"))
    }

    /** The dialog that asks to change the approximate location to the precise one */
    fun systemUpgradeToPrecise() {
        waitFor(By.res(PERMISSION_CONTROLLER, "permission_location_accuracy_fine_only"))
        clickSystem("permission_allow_foreground_only_button")
        waitGone(By.res(PERMISSION_CONTROLLER, "grant_dialog"))
    }

    private fun clickSystem(id: String) = click(By.res(PERMISSION_CONTROLLER, id))

    companion object {
        const val PACKAGE = "com.h0tk3y.rally.debug"
        const val ACTIVITY = "com.h0tk3y.rally.android.MainActivity"
        const val RACE_SERVICE = "com.h0tk3y.rally.android.racecervice.LocalRaceService"
        const val PERMISSION_CONTROLLER = "com.android.permissioncontroller"
        const val TIMEOUT = 10_000L

        const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
        val LOCATION = arrayOf(FINE_LOCATION, COARSE_LOCATION)
        const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        val BLUETOOTH = arrayOf(BLUETOOTH_CONNECT)

        // ServiceInfo.FOREGROUND_SERVICE_TYPE_*
        const val TYPE_LOCATION = 0x8
        const val TYPE_CONNECTED_DEVICE = 0x10

        const val RACE_MODE = "Race mode"
        const val GPS_TITLE = "GPS access"
        const val GPS_REASON = "needs access to the precise location"
        const val GPS_APPROXIMATE = "only has access to the approximate location"
        const val GPS_NO_PERMISSION = "GPS: permissions!"
        const val OBD_TITLE = "Bluetooth access"
        const val OBD_REASON = "needs access to nearby devices"
    }
}
