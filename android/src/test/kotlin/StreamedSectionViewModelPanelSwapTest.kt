package com.h0tk3y.rally.android.scenes

import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.UserPreferences
import com.h0tk3y.rally.android.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class StreamedSectionViewModelPanelSwapTest {
    @get:Rule
    val withMain = MainDispatcherRule()

    private val preferences = MutableStateFlow(
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

    private val prefs = mockk<PreferenceRepository> {
        every { userPreferencesFlow }.returns(preferences)
        coEvery { saveHudSwapPanels(any()) }.answers {
            preferences.value = preferences.value.copy(hudSwapPanels = firstArg())
        }
    }

    @Test
    fun `panels are not swapped by default`() = runTest(withMain.dispatcher) {
        val model = StreamedSectionViewModel(prefs, withMain.dispatcher)
        runCurrent()
        assertFalse(model.panelsSwapped.value)
    }

    @Test
    fun `panels swap state follows the saved preference`() = runTest(withMain.dispatcher) {
        preferences.value = preferences.value.copy(hudSwapPanels = true)
        val model = StreamedSectionViewModel(prefs, withMain.dispatcher)
        runCurrent()
        assertTrue(model.panelsSwapped.value)

        preferences.value = preferences.value.copy(hudSwapPanels = false)
        runCurrent()
        assertFalse(model.panelsSwapped.value)
    }

    @Test
    fun `toggling saves the opposite value`() = runTest(withMain.dispatcher) {
        val model = StreamedSectionViewModel(prefs, withMain.dispatcher)
        runCurrent()

        model.togglePanelsSwapped()
        runCurrent()
        coVerify(exactly = 1) { prefs.saveHudSwapPanels(true) }
        assertTrue(model.panelsSwapped.value)

        model.togglePanelsSwapped()
        runCurrent()
        coVerify(exactly = 1) { prefs.saveHudSwapPanels(false) }
        assertFalse(model.panelsSwapped.value)
    }

    @Test
    fun `the regular section view does not support swapping panels`() {
        assertFalse(PanelSwapControls::class.java.isAssignableFrom(LiveSectionViewModel::class.java))
    }
}
