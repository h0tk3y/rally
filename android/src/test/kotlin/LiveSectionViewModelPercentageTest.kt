package com.h0tk3y.rally.android.scenes

import androidx.lifecycle.viewModelScope
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.PlainSpeedKmh
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.SpeedWithPercentage
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.testing.MainDispatcherRule
import com.h0tk3y.rally.android.testing.simplePositions
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.android.views.StartOption
import com.h0tk3y.rally.modifier
import defaultPreferencesMock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import com.h0tk3y.rally.android.racecervice.PrimaryRaceService
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class LiveSectionViewModelPercentageTest {
    @get:Rule
    val withMain = MainDispatcherRule()

    val db = InMemoryDatabaseOperations()

    @Test
    fun `toggle percentage on position with speed`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Select first line (which has setavg 60.0)
            viewModel.selectLine(LineNumber(1, 0), DataKind.AverageSpeed)
            repeat(10) { runCurrent() }

            // Initially no lastEnteredPercent
            assertNull(viewModel.editorState.value.lastEnteredPercent)

            // Press PERCENT to attach percentage (default 100%)
            viewModel.keyPress(GridKey.PERCENT)
            repeat(10) { runCurrent() }

            assertEquals(DataKind.SpeedPercentage, viewModel.editorFocus.value.kind)
            val line1 = viewModel.inputPositions.value.first() as PositionLine
            val setAvg = line1.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvg)
            assertEquals(60.0, setAvg.baseSpeedKmh)
            assertEquals(100.0, setAvg.percentage)

            // Set percentage to 50%
            viewModel.setSpeedLimitPercent("50")
            viewModel.keyPress(GridKey.APPLY_LAST_PERCENT)
            repeat(10) { runCurrent() }

            assertEquals("50", viewModel.editorState.value.lastEnteredPercent)
            val line1Updated = viewModel.inputPositions.value.first() as PositionLine
            val setAvg50 = line1Updated.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvg50)
            assertEquals(50.0, setAvg50.percentage)
            assertEquals(30.0, setAvg50.valueKmh)

            // Select line 2, add SETAVG, then press APPLY_LAST_PERCENT
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            viewModel.keyPress(GridKey.SETAVG)
            viewModel.keyPress(GridKey.APPLY_LAST_PERCENT)
            repeat(10) { runCurrent() }

            val line2 = viewModel.inputPositions.value[1] as PositionLine
            val setAvgLine2 = line2.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvgLine2)
            assertEquals(50.0, setAvgLine2.percentage)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `cannot enable or apply percentage on position without speed`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Line 1 has setavg -> canSetPercent should be true
            viewModel.selectLine(LineNumber(1, 0), DataKind.AverageSpeed)
            repeat(10) { runCurrent() }
            assertTrue(viewModel.editorState.value.canSetPercent)

            // Line 2 has no speed modifier (only distance) -> canSetPercent should be false
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            assertFalse(viewModel.editorState.value.canSetPercent)
            val line2Before = viewModel.inputPositions.value[1] as PositionLine
            assertNull(line2Before.modifier<PositionLineModifier.SetAvg>())

            // Press PERCENT -> should not add SetAvgSpeed or enable percentage
            viewModel.keyPress(GridKey.PERCENT)
            repeat(10) { runCurrent() }

            val line2AfterPercent = viewModel.inputPositions.value[1] as PositionLine
            assertNull(line2AfterPercent.modifier<PositionLineModifier.SetAvg>())
            assertEquals(DataKind.Distance, viewModel.editorFocus.value.kind)
            assertFalse(viewModel.editorState.value.canSetPercent)

            // Set last entered percent and press APPLY_LAST_PERCENT -> should do nothing
            viewModel.setSpeedLimitPercent("75")
            viewModel.keyPress(GridKey.APPLY_LAST_PERCENT)
            repeat(10) { runCurrent() }

            val line2AfterApply = viewModel.inputPositions.value[1] as PositionLine
            assertNull(line2AfterApply.modifier<PositionLineModifier.SetAvg>())
            assertEquals(DataKind.Distance, viewModel.editorFocus.value.kind)
            assertFalse(viewModel.editorState.value.canSetPercent)

            // Add ENDAVG to line 2 (endavg has no speed) -> canSetPercent should still be false
            viewModel.keyPress(GridKey.ENDAVG)
            repeat(10) { runCurrent() }
            val line2WithEndAvg = viewModel.inputPositions.value[1] as PositionLine
            assertNotNull(line2WithEndAvg.modifier<PositionLineModifier.EndAvg>())
            assertNull(line2WithEndAvg.modifier<PositionLineModifier.SetAvg>())
            assertFalse(viewModel.editorState.value.canSetPercent)

            // Press PERCENT on line with only ENDAVG -> should do nothing
            viewModel.keyPress(GridKey.PERCENT)
            repeat(10) { runCurrent() }
            val line2AfterPercentEndAvg = viewModel.inputPositions.value[1] as PositionLine
            assertNull(line2AfterPercentEndAvg.modifier<PositionLineModifier.SetAvg>())
            assertFalse(viewModel.editorState.value.canSetPercent)

            // Press APPLY_LAST_PERCENT on line with only ENDAVG -> should do nothing
            viewModel.keyPress(GridKey.APPLY_LAST_PERCENT)
            repeat(10) { runCurrent() }
            val line2AfterApplyEndAvg = viewModel.inputPositions.value[1] as PositionLine
            assertNull(line2AfterApplyEndAvg.modifier<PositionLineModifier.SetAvg>())
            assertFalse(viewModel.editorState.value.canSetPercent)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `percentage update in going state updates rememberSpeed immediately`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        val raceModel = RaceModel(
            kotlin.time.Instant.DISTANT_PAST,
            DistanceKm.zero,
            DistanceKm.zero,
            DistanceKm.zero,
            true,
            SpeedKmh(10.0)
        )
        val raceStateFlow = MutableStateFlow<RaceState>(RaceState.Going(sec.section.id, raceModel, null, null))
        val rememberSpeedFlow = MutableStateFlow<SpeedKmh?>(PlainSpeedKmh(60.0))
        val speedLimitPercentFlow = MutableStateFlow<String?>(null)

        val service = mockk<PrimaryRaceService>(relaxed = true) {
            every { raceState }.returns(raceStateFlow)
            every { rememberSpeedLimit }.returns(rememberSpeedFlow)
            every { speedLimitPercent }.returns(speedLimitPercentFlow)
            every { telemetryPublicState }.returns(MutableStateFlow(TelemetryPublicState.GpsWaiting(0)))
            every { setSpeedLimitPercent(any()) } answers {
                val percent = firstArg<String?>()
                speedLimitPercentFlow.value = percent
                val cur = rememberSpeedFlow.value
                if (cur != null) {
                    val p = percent?.toDoubleOrNull()
                    rememberSpeedFlow.value = if (p != null && p != 100.0) {
                        SpeedWithPercentage(PlainSpeedKmh(cur.baseSpeedKmh), p)
                    } else {
                        PlainSpeedKmh(cur.baseSpeedKmh)
                    }
                }
            }
        }

        try {
            repeat(10) { runCurrent() }
            viewModel.onServiceConnected(service)
            repeat(10) { runCurrent() }

            viewModel.setSpeedLimitPercent("80")
            repeat(10) { runCurrent() }

            val remember = viewModel.rememberSpeed.value
            val rememberWithPercent = assertIs<SpeedWithPercentage>(remember)
            assertEquals(60.0, rememberWithPercent.baseSpeedKmh)
            assertEquals(80.0, rememberWithPercent.percentage)
            assertEquals(48.0, rememberWithPercent.valueKmh)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `percentage update in in-race state does not update current rememberSpeed until next speed set`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        val raceModel = RaceModel(
            kotlin.time.Instant.DISTANT_PAST,
            DistanceKm.zero,
            DistanceKm.zero,
            DistanceKm.zero,
            true,
            SpeedKmh(10.0)
        )
        val raceStateFlow = MutableStateFlow<RaceState>(RaceState.InRace(sec.section.id, raceModel, null, null, raceModel))
        val rememberSpeedFlow = MutableStateFlow<SpeedKmh?>(PlainSpeedKmh(60.0))
        val speedLimitPercentFlow = MutableStateFlow<String?>(null)

        val service = mockk<PrimaryRaceService>(relaxed = true) {
            every { raceState }.returns(raceStateFlow)
            every { rememberSpeedLimit }.returns(rememberSpeedFlow)
            every { speedLimitPercent }.returns(speedLimitPercentFlow)
            every { telemetryPublicState }.returns(MutableStateFlow(TelemetryPublicState.GpsWaiting(0)))
            every { setSpeedLimitPercent(any()) } answers {
                speedLimitPercentFlow.value = firstArg()
            }
        }

        try {
            repeat(10) { runCurrent() }
            viewModel.onServiceConnected(service)
            repeat(10) { runCurrent() }

            viewModel.setSpeedLimitPercent("80")
            repeat(10) { runCurrent() }

            // Current rememberSpeed remains unchanged in InRace
            val remember = viewModel.rememberSpeed.value
            val plainSpeed = assertIs<PlainSpeedKmh>(remember)
            assertEquals(60.0, plainSpeed.valueKmh)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
