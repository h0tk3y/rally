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

    @Test
    fun `percentage field deletion shows hint and replaces on input`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Select line 1 (which has setavg 60.0)
            viewModel.selectLine(LineNumber(1, 0), DataKind.AverageSpeed)
            repeat(10) { runCurrent() }

            // Attach percentage
            viewModel.keyPress(GridKey.PERCENT)
            repeat(10) { runCurrent() }

            // Attaching percentage sets rawText to "" with default 100% in model
            assertEquals(DataKind.SpeedPercentage, viewModel.editorFocus.value.kind)
            assertEquals("", viewModel.editorFocus.value.rawText)
            assertEquals(0, viewModel.editorFocus.value.cursor)
            val line1 = viewModel.inputPositions.value.first() as PositionLine
            val setAvg = line1.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvg)
            assertEquals(100.0, setAvg.percentage)

            // Entering '8' replaces hint with '8'
            viewModel.keyPress(GridKey.N_8)
            repeat(10) { runCurrent() }
            assertNull(viewModel.editorFocus.value.rawText)
            assertEquals(1, viewModel.editorFocus.value.cursor)
            val line1After8 = viewModel.inputPositions.value.first() as PositionLine
            val setAvg8 = line1After8.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvg8)
            assertEquals(8.0, setAvg8.percentage)

            // Entering '0' gives '80'
            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }
            assertNull(viewModel.editorFocus.value.rawText)
            assertEquals(2, viewModel.editorFocus.value.cursor)
            val line1After80 = viewModel.inputPositions.value.first() as PositionLine
            val setAvg80 = line1After80.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvg80)
            assertEquals(80.0, setAvg80.percentage)

            // Deleting characters: 80 -> 8 -> ""
            viewModel.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }
            assertNull(viewModel.editorFocus.value.rawText)
            assertEquals(1, viewModel.editorFocus.value.cursor)

            viewModel.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }
            assertEquals("", viewModel.editorFocus.value.rawText)
            assertEquals(0, viewModel.editorFocus.value.cursor)

            // Model gets 100% when draft is empty
            val line1AfterDel = viewModel.inputPositions.value.first() as PositionLine
            val setAvgAfterDel = line1AfterDel.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvgAfterDel)
            assertEquals(100.0, setAvgAfterDel.percentage)

            // Entering '5' again replaces hint with '5'
            viewModel.keyPress(GridKey.N_5)
            repeat(10) { runCurrent() }
            assertNull(viewModel.editorFocus.value.rawText)
            val line1After5 = viewModel.inputPositions.value.first() as PositionLine
            val setAvg5 = line1After5.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvg5)
            assertEquals(5.0, setAvg5.percentage)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `percentage field supports trailing dot and zeroes`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            viewModel.selectLine(LineNumber(1, 0), DataKind.AverageSpeed)
            viewModel.keyPress(GridKey.PERCENT)
            repeat(10) { runCurrent() }

            // Enter 8, 0, ., 0, 5
            viewModel.keyPress(GridKey.N_8)
            viewModel.keyPress(GridKey.N_0)
            assertNull(viewModel.editorFocus.value.rawText)

            viewModel.keyPress(GridKey.DOT)
            repeat(10) { runCurrent() }
            assertEquals("80.", viewModel.editorFocus.value.rawText)
            assertEquals(3, viewModel.editorFocus.value.cursor)
            val lineAfterDot = viewModel.inputPositions.value.first() as PositionLine
            assertEquals(80.0, (lineAfterDot.modifier<PositionLineModifier.SetAvg>()!!.setavg as SpeedWithPercentage).percentage)

            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }
            assertEquals("80.0", viewModel.editorFocus.value.rawText)
            assertEquals(4, viewModel.editorFocus.value.cursor)
            val lineAfterDot0 = viewModel.inputPositions.value.first() as PositionLine
            assertEquals(80.0, (lineAfterDot0.modifier<PositionLineModifier.SetAvg>()!!.setavg as SpeedWithPercentage).percentage)

            viewModel.keyPress(GridKey.N_5)
            repeat(10) { runCurrent() }
            assertNull(viewModel.editorFocus.value.rawText)
            assertEquals(5, viewModel.editorFocus.value.cursor)
            val lineAfterDot05 = viewModel.inputPositions.value.first() as PositionLine
            assertEquals(80.05, (lineAfterDot05.modifier<PositionLineModifier.SetAvg>()!!.setavg as SpeedWithPercentage).percentage)

            // Moving to previous line / another field clears rawText and commits canonical format
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            assertNull(viewModel.editorFocus.value.rawText)

            // Select line 1 percentage again
            viewModel.selectLine(LineNumber(1, 0), DataKind.SpeedPercentage)
            repeat(10) { runCurrent() }
            assertNull(viewModel.editorFocus.value.rawText)
            val line1 = viewModel.inputPositions.value.first() as PositionLine
            assertEquals(80.05, (line1.modifier<PositionLineModifier.SetAvg>()!!.setavg as SpeedWithPercentage).percentage)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `adding percentage without provided value is empty with 100 hint and adding with provided value applies directly`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Set last entered percent to 75%
            viewModel.setSpeedLimitPercent("75")
            repeat(10) { runCurrent() }
            assertEquals("75", viewModel.editorState.value.lastEnteredPercent)

            // Select line 1 (which has setavg 60.0 without percentage)
            viewModel.selectLine(LineNumber(1, 0), DataKind.AverageSpeed)
            repeat(10) { runCurrent() }

            // Press PERCENT (add without provided value)
            viewModel.keyPress(GridKey.PERCENT)
            repeat(10) { runCurrent() }

            // Should add as 100% in model, and focus with rawText = "" (100% hint)
            assertEquals(DataKind.SpeedPercentage, viewModel.editorFocus.value.kind)
            assertEquals("", viewModel.editorFocus.value.rawText)
            assertEquals(0, viewModel.editorFocus.value.cursor)
            val line1 = viewModel.inputPositions.value.first() as PositionLine
            val setAvg1 = line1.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvg1)
            assertEquals(100.0, setAvg1.percentage)

            // Select line 2, add SETAVG (speed 0.0)
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            viewModel.keyPress(GridKey.SETAVG)
            repeat(10) { runCurrent() }

            // Press APPLY_LAST_PERCENT (add with provided value 75%)
            viewModel.keyPress(GridKey.APPLY_LAST_PERCENT)
            repeat(10) { runCurrent() }

            // Should immediately apply 75% in a single step with rawText = null
            assertEquals(DataKind.SpeedPercentage, viewModel.editorFocus.value.kind)
            assertNull(viewModel.editorFocus.value.rawText)
            val line2 = viewModel.inputPositions.value[1] as PositionLine
            val setAvg2 = line2.modifier<PositionLineModifier.SetAvg>()!!.setavg
            assertIs<SpeedWithPercentage>(setAvg2)
            assertEquals(75.0, setAvg2.percentage)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `decimal editing handles leading zero exclusion and trailing zero navigation exceptions`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Select line 1, AverageSpeed (60)
            viewModel.selectLine(LineNumber(1, 0), DataKind.AverageSpeed)
            repeat(10) { runCurrent() }
            viewModel.keyPress(GridKey.PERCENT)
            repeat(10) { runCurrent() }

            // Field is empty percentage (rawText = "")
            assertEquals("", viewModel.editorFocus.value.rawText)

            // Exception 1: adding leading '0' to integral part is ignored
            // Typing '8' gives "8"
            viewModel.keyPress(GridKey.N_8)
            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }
            assertEquals(null, viewModel.editorFocus.value.rawText) // Canonical "80" matches, rawText is null
            assertEquals(2, viewModel.editorFocus.value.cursor)

            // Move cursor to 0 and type '0' -> should be ignored (no leading zero introduced)
            viewModel.keyPress(GridKey.LEFT)
            viewModel.keyPress(GridKey.LEFT)
            assertEquals(0, viewModel.editorFocus.value.cursor)
            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }
            assertEquals(0, viewModel.editorFocus.value.cursor)
            val line1 = viewModel.inputPositions.value.first() as PositionLine
            assertEquals(80.0, (line1.modifier<PositionLineModifier.SetAvg>()!!.setavg as SpeedWithPercentage).percentage)

            // Move to end (cursor 2), enter dot -> "80.0" with cursor at 3 ("80.|0")
            viewModel.keyPress(GridKey.RIGHT)
            viewModel.keyPress(GridKey.RIGHT)
            viewModel.keyPress(GridKey.DOT)
            repeat(10) { runCurrent() }
            assertEquals(3, viewModel.editorFocus.value.cursor)

            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }
            assertEquals("80.0", viewModel.editorFocus.value.rawText)
            assertEquals(4, viewModel.editorFocus.value.cursor)

            // Exception 2: cursor between dot and single trailing zero
            // Move cursor left (now at index 3, between '.' and '0')
            viewModel.keyPress(GridKey.LEFT)
            assertEquals(3, viewModel.editorFocus.value.cursor)

            // Entering '0' should NOT insert another zero between; cursor should just move behind '0' to index 4
            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }
            assertEquals(4, viewModel.editorFocus.value.cursor)
            assertEquals("80.0", viewModel.editorFocus.value.rawText)

            // Now that cursor is behind the trailing zero, entering '0' inserts second trailing zero -> "80.00"
            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }
            assertEquals(5, viewModel.editorFocus.value.cursor)
            assertEquals("80.00", viewModel.editorFocus.value.rawText)

            // Entering '5' -> "80.005"
            viewModel.keyPress(GridKey.N_5)
            repeat(10) { runCurrent() }
            assertEquals(6, viewModel.editorFocus.value.cursor)
            // "80.005" matches canonical representation, so rawText is null
            assertNull(viewModel.editorFocus.value.rawText)
            val line1Final = viewModel.inputPositions.value.first() as PositionLine
            assertEquals(80.005, (line1Final.modifier<PositionLineModifier.SetAvg>()!!.setavg as SpeedWithPercentage).percentage)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `adding setavg modifier starts with 0_0 placeholder and typing 60 immediately produces 60_0`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Line 2 has no speed modifier. Select Line 2 distance.
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }

            // Press SETAVG to add speed modifier
            viewModel.keyPress(GridKey.SETAVG)
            repeat(10) { runCurrent() }

            // Should focus on AverageSpeed with empty rawText ("" for "0.0" placeholder hint), cursor 0
            assertEquals(DataKind.AverageSpeed, viewModel.editorFocus.value.kind)
            assertEquals("", viewModel.editorFocus.value.rawText)
            assertEquals(0, viewModel.editorFocus.value.cursor)
            val line2 = viewModel.inputPositions.value[1] as PositionLine
            assertEquals(0.0, line2.modifier<PositionLineModifier.SetAvg>()!!.setavg.valueKmh)

            // Entering '6' should replace placeholder with '6' (speed 6.0, cursor 1)
            viewModel.keyPress(GridKey.N_6)
            repeat(10) { runCurrent() }
            assertEquals(1, viewModel.editorFocus.value.cursor)
            val line2After6 = viewModel.inputPositions.value[1] as PositionLine
            assertEquals(6.0, line2After6.modifier<PositionLineModifier.SetAvg>()!!.setavg.valueKmh)

            // Entering '0' should produce '60.0' (speed 60.0, cursor 2)
            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }
            assertEquals(2, viewModel.editorFocus.value.cursor)
            val line2After60 = viewModel.inputPositions.value[1] as PositionLine
            assertEquals(60.0, line2After60.modifier<PositionLineModifier.SetAvg>()!!.setavg.valueKmh)

            // Redundant selectLine on same line and field does not clear rawText or reset cursor
            viewModel.selectLine(LineNumber(2, 0), DataKind.AverageSpeed)
            repeat(10) { runCurrent() }
            assertEquals(2, viewModel.editorFocus.value.cursor)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `entering digit at 0 dot 0 replaces leading zero and places cursor after digit`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Line 1 distance is 0.0. Select Line 1 distance -> cursor is already at 1 ("0|.0")
            viewModel.selectLine(LineNumber(1, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            assertEquals(1, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)

            // Entering '0' should be ignored (no leading zero)
            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }
            assertEquals(1, viewModel.editorFocus.value.cursor)

            // Entering '5' at "0|.0" should result in "5.0" with cursor at 1 (5|.0) and rawText = null
            viewModel.keyPress(GridKey.N_5)
            repeat(10) { runCurrent() }

            assertEquals(1, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val currentItemAfter5 = viewModel.inputPositions.value.find { it.lineNumber == viewModel.selectedLineIndex.value } as PositionLine
            assertEquals(5.0, currentItemAfter5.atKm.valueKm)

            // Entering another digit '2' at "5|.0" should result in "52.0" with cursor at 2 (52|.0)
            viewModel.keyPress(GridKey.N_2)
            repeat(10) { runCurrent() }

            assertEquals(2, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val currentItemAfter52 = viewModel.inputPositions.value.find { it.lineNumber == viewModel.selectedLineIndex.value } as PositionLine
            assertEquals(52.0, currentItemAfter52.atKm.valueKm)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `deleting character at end of 1 dot 0 ends up as 1 dot cursor 0`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 2.0, 2)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Line 2 distance is 1.0. Select Line 2 distance and move cursor to end: "1.0|" (cursor = 3)
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            viewModel.keyPress(GridKey.RIGHT)
            viewModel.keyPress(GridKey.RIGHT)
            viewModel.keyPress(GridKey.RIGHT)
            repeat(10) { runCurrent() }
            assertEquals(3, viewModel.editorFocus.value.cursor)

            // Deleting character at "1.0|" should keep single trailing zero and place cursor at "1.|0" (cursor = 2)
            viewModel.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }

            assertEquals(2, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val line2 = viewModel.inputPositions.value[1] as PositionLine
            assertEquals(1.0, line2.atKm.valueKm)

            // Deleting again at "1.|0" moves cursor before dot: "1|.0" (cursor = 1)
            viewModel.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }

            assertEquals(1, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `entering digit at 1 dot cursor 0 replaces trailing zero and places cursor after digit`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 2.0, 2)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Line 2 distance is 1.0. Select Line 2 distance and move cursor to "1.0|"
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            repeat(3) { viewModel.keyPress(GridKey.RIGHT) }
            repeat(10) { runCurrent() }
            assertEquals(3, viewModel.editorFocus.value.cursor)

            // Deleting character at "1.0|" keeps trailing zero and moves cursor to "1.|0"
            viewModel.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }
            assertEquals(2, viewModel.editorFocus.value.cursor)

            // Entering '5' at "1.|0" should replace trailing zero and become "1.5|" (cursor = 3, rawText = null)
            viewModel.keyPress(GridKey.N_5)
            repeat(10) { runCurrent() }

            assertEquals(3, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val line2 = viewModel.inputPositions.value[1] as PositionLine
            assertEquals(1.5, line2.atKm.valueKm)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `entering digits beyond 3 fractional digits is ignored and delete works immediately`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 2.0, 2)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Set Line 2 distance to 1.315
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            // Move to end ("1.0|")
            repeat(3) { viewModel.keyPress(GridKey.RIGHT) }
            repeat(10) { runCurrent() }
            // Delete '0', enter '3', '1', '5' -> "1.315|" (cursor = 5)
            viewModel.keyPress(GridKey.DEL)
            viewModel.keyPress(GridKey.N_3)
            viewModel.keyPress(GridKey.N_1)
            viewModel.keyPress(GridKey.N_5)
            repeat(10) { runCurrent() }

            assertEquals(5, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val line2 = viewModel.inputPositions.value[1] as PositionLine
            assertEquals(1.315, line2.atKm.valueKm)

            // Entering "1", "2", "3" should all be ignored because 3 fractional digits are already present
            viewModel.keyPress(GridKey.N_1)
            viewModel.keyPress(GridKey.N_2)
            viewModel.keyPress(GridKey.N_3)
            repeat(10) { runCurrent() }

            assertEquals(5, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)

            // A single delete should immediately remove '5' and produce "1.31|" (cursor = 4)
            viewModel.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }

            assertEquals(4, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val line2AfterDel = viewModel.inputPositions.value[1] as PositionLine
            assertEquals(1.31, line2AfterDel.atKm.valueKm)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `entering digits beyond 3 fractional digits with trailing zero is consistently ignored`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 2.0, 2)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Set Line 2 distance to 1.310 (which uses rawText = "1.310")
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            repeat(3) { viewModel.keyPress(GridKey.RIGHT) }
            repeat(10) { runCurrent() }
            // Delete '0', enter '3', '1', '0' -> "1.310|"
            viewModel.keyPress(GridKey.DEL)
            viewModel.keyPress(GridKey.N_3)
            viewModel.keyPress(GridKey.N_1)
            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }

            assertEquals(5, viewModel.editorFocus.value.cursor)
            assertEquals("1.310", viewModel.editorFocus.value.rawText)

            // Entering "1", "2", "0" should all be ignored because 3 fractional digits are already present
            viewModel.keyPress(GridKey.N_1)
            viewModel.keyPress(GridKey.N_2)
            viewModel.keyPress(GridKey.N_0)
            repeat(10) { runCurrent() }

            assertEquals(5, viewModel.editorFocus.value.cursor)
            assertEquals("1.310", viewModel.editorFocus.value.rawText)

            // A single delete should immediately remove '0' and produce "1.31|" (cursor = 4, rawText = null)
            viewModel.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }

            assertEquals(4, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val line2AfterDel = viewModel.inputPositions.value[1] as PositionLine
            assertEquals(1.31, line2AfterDel.atKm.valueKm)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `enabling editor initializes cursor at reasonable position for distance`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", "0.0\n1.0\n2.5") as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }

            // Initially on line 1 ("0.0"). Enable editor -> cursor should be 1 ("0|.0")
            viewModel.switchEditor()
            repeat(10) { runCurrent() }
            assertEquals(1, viewModel.editorFocus.value.cursor)

            // Disable editor and select line 2 ("1.0")
            viewModel.switchEditor()
            repeat(10) { runCurrent() }
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }

            // Enable editor on line 2 -> cursor should be 1 ("1|.0")
            viewModel.switchEditor()
            repeat(10) { runCurrent() }
            assertEquals(1, viewModel.editorFocus.value.cursor)

            // Disable editor and select line 3 ("2.5")
            viewModel.switchEditor()
            repeat(10) { runCurrent() }
            viewModel.selectLine(LineNumber(3, 0), DataKind.Distance)
            repeat(10) { runCurrent() }

            // Enable editor on line 3 -> cursor should be 3 ("2.5|")
            viewModel.switchEditor()
            repeat(10) { runCurrent() }
            assertEquals(3, viewModel.editorFocus.value.cursor)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `entering digit inside fractional part with 3 digits supersedes digits`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", "0.123") as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Line 1 distance is 0.123. Reasonable start position puts cursor at 5 ("0.123|")
            assertEquals(5, viewModel.editorFocus.value.cursor)

            // Move cursor left twice: "0.123|" -> "0.12|3" -> "0.1|23" (cursor = 3)
            viewModel.keyPress(GridKey.LEFT)
            viewModel.keyPress(GridKey.LEFT)
            repeat(10) { runCurrent() }
            assertEquals(3, viewModel.editorFocus.value.cursor)

            // Press '5' at "0.1|23" -> should supersede '3', becoming "0.152" with cursor at 4 ("0.15|2")
            viewModel.keyPress(GridKey.N_5)
            repeat(10) { runCurrent() }

            assertEquals(4, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val line1 = viewModel.inputPositions.value.first() as PositionLine
            assertEquals(0.152, line1.atKm.valueKm)

            // Move cursor left: "0.15|2" -> "0.1|52" -> "0.|152" (cursor = 2)
            viewModel.keyPress(GridKey.LEFT)
            viewModel.keyPress(GridKey.LEFT)
            repeat(10) { runCurrent() }
            assertEquals(2, viewModel.editorFocus.value.cursor)

            // Press '9' at "0.|152" -> should become "0.915" with cursor at 3 ("0.9|15")
            viewModel.keyPress(GridKey.N_9)
            repeat(10) { runCurrent() }

            assertEquals(3, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val line1After9 = viewModel.inputPositions.value.first() as PositionLine
            assertEquals(0.915, line1After9.atKm.valueKm)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `entering digit at pipe 0 dot 0 inserts before zero resulting in 1 pipe 0 dot 0`() = runTest(withMain.dispatcher) {
        val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Line 1 distance is 0.0. Reasonable start position is at 1 ("0|.0")
            viewModel.selectLine(LineNumber(1, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            assertEquals(1, viewModel.editorFocus.value.cursor)

            // Move cursor left to 0 ("|0.0")
            viewModel.keyPress(GridKey.LEFT)
            repeat(10) { runCurrent() }
            assertEquals(0, viewModel.editorFocus.value.cursor)

            // Press '1' at "|0.0" -> should become "10.0" with cursor at 1 ("1|0.0")
            viewModel.keyPress(GridKey.N_1)
            repeat(10) { runCurrent() }

            assertEquals(1, viewModel.editorFocus.value.cursor)
            assertEquals(null, viewModel.editorFocus.value.rawText)
            val currentItem = viewModel.inputPositions.value.find { it.lineNumber == viewModel.selectedLineIndex.value } as PositionLine
            assertEquals(10.0, currentItem.atKm.valueKm)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
