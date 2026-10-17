package com.h0tk3y.rally.android.scenes

import androidx.lifecycle.viewModelScope
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.testing.MainDispatcherRule
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.modifier
import defaultPreferencesMock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSectionViewModelDistanceReorderingTest {

    @get:Rule
    val withMain = MainDispatcherRule()

    val db = InMemoryDatabaseOperations()

    @Test
    fun testDistanceEditMovesItemAndMaintainsSelection() = runTest(withMain.dispatcher) {
        val sec = db.createSection("TestSection", null) as SectionInsertOrRenameResult.Success
        db.updateSectionPositions(
            sec.section.id,
            """
                1.0 setavg 50
                3.0 setavg 60
                5.0 setavg 70
            """.trimIndent()
        )

        val viewModel = LiveSectionViewModel(
            sec.section.id,
            db,
            defaultPreferencesMock(),
            withMain.dispatcher
        )

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Select Line 2 (at 3.0 km) on Distance field
            viewModel.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            assertEquals(LineNumber(2, 0), viewModel.selectedLineIndex.value)
            assertEquals(DataKind.Distance, viewModel.editorFocus.value.kind)

            // Cursor is at start or end. Let's enter digits to change 3.0 -> 39.0
            viewModel.keyPress(GridKey.N_9)
            repeat(10) { runCurrent() }

            // Now positions should be 1.0, 5.0, 39.0 -> the item is now at Line 3 (39.0 km)
            val positionsAfter9 = viewModel.inputPositions.value.filterIsInstance<PositionLine>()
            assertEquals(3, positionsAfter9.size)
            assertEquals(1.0, positionsAfter9[0].atKm.valueKm)
            assertEquals(5.0, positionsAfter9[1].atKm.valueKm)
            assertEquals(39.0, positionsAfter9[2].atKm.valueKm)
            // It kept its modifier (setavg 60)
            assertEquals(60.0, positionsAfter9[2].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            // And selectedLineIndex should now be LineNumber(3, 0)
            assertEquals(LineNumber(3, 0), viewModel.selectedLineIndex.value)

            // Now delete character '9' using backspace -> distance becomes 3.0 km again
            viewModel.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }

            val positionsAfterDel = viewModel.inputPositions.value.filterIsInstance<PositionLine>()
            assertEquals(3, positionsAfterDel.size)
            assertEquals(1.0, positionsAfterDel[0].atKm.valueKm)
            assertEquals(3.0, positionsAfterDel[1].atKm.valueKm)
            assertEquals(5.0, positionsAfterDel[2].atKm.valueKm)
            assertEquals(60.0, positionsAfterDel[1].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            // Selected line is back to LineNumber(2, 0)
            assertEquals(LineNumber(2, 0), viewModel.selectedLineIndex.value)

            // Now decrease distance below 1.0 km (e.g. edit distance to 0.0 km)
            // First select line 2 distance, delete until 0.0
            viewModel.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }
            // Now at 0.0 km, it moves before 1.0 km (to index 0, LineNumber(1, 0))
            val positionsAfter0 = viewModel.inputPositions.value.filterIsInstance<PositionLine>()
            assertEquals(0.0, positionsAfter0[0].atKm.valueKm)
            assertEquals(1.0, positionsAfter0[1].atKm.valueKm)
            assertEquals(5.0, positionsAfter0[2].atKm.valueKm)
            assertEquals(LineNumber(1, 0), viewModel.selectedLineIndex.value)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun testPositionMovementUpAndDownRelativeOrder() = runTest(withMain.dispatcher) {
        // Test moving UP: should end up below all positions with same distance already above it, but not below it
        val secUp = db.createSection("TestSectionUp", null) as SectionInsertOrRenameResult.Success
        db.updateSectionPositions(
            secUp.section.id,
            """
                10.0 setavg 10
                10.0 setavg 20
                20.0 setavg 30
                10.0 setavg 40
                30.0 setavg 50
            """.trimIndent()
        )

        val vmUp = LiveSectionViewModel(
            secUp.section.id,
            db,
            defaultPreferencesMock(),
            withMain.dispatcher
        )

        try {
            repeat(10) { runCurrent() }
            vmUp.switchEditor()
            repeat(10) { runCurrent() }

            // Select Line 3 (20.0 km, setavg 30) on Distance field
            vmUp.selectLine(LineNumber(3, 0), DataKind.Distance)
            repeat(10) { runCurrent() }

            // Change distance from 20.0 to 10.0 directly
            val item = vmUp.inputPositions.value.filterIsInstance<PositionLine>().first { it.lineNumber == LineNumber(3, 0) }
            vmUp.updateCurrentText(item, vmUp.editorFocus.value, "10.0")
            repeat(10) { runCurrent() }

            val positionsUp = vmUp.inputPositions.value.filterIsInstance<PositionLine>()
            assertEquals(5, positionsUp.size)
            // Lines 1 and 2 (setavg 10, 20) were above it -> edited item (setavg 30) is below them at index 2 (Line 3)
            // Line 4 (setavg 40) was below it -> edited item is above it
            assertEquals(10.0, positionsUp[0].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(20.0, positionsUp[1].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(30.0, positionsUp[2].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(40.0, positionsUp[3].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(50.0, positionsUp[4].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(LineNumber(3, 0), vmUp.selectedLineIndex.value)
        } finally {
            vmUp.viewModelScope.cancel()
        }

        // Test moving DOWN: should end up above all positions with same distance already below it
        val secDown = db.createSection("TestSectionDown", null) as SectionInsertOrRenameResult.Success
        db.updateSectionPositions(
            secDown.section.id,
            """
                5.0 setavg 10
                2.0 setavg 20
                10.0 setavg 30
                10.0 setavg 40
                20.0 setavg 50
            """.trimIndent()
        )

        val vmDown = LiveSectionViewModel(
            secDown.section.id,
            db,
            defaultPreferencesMock(),
            withMain.dispatcher
        )

        try {
            repeat(10) { runCurrent() }
            vmDown.switchEditor()
            repeat(10) { runCurrent() }

            // Select Line 2 (2.0 km, setavg 20) on Distance field
            vmDown.selectLine(LineNumber(2, 0), DataKind.Distance)
            repeat(10) { runCurrent() }

            // Change distance from 2.0 to 10.0 directly (moves down)
            val item = vmDown.inputPositions.value.filterIsInstance<PositionLine>().first { it.lineNumber == LineNumber(2, 0) }
            vmDown.updateCurrentText(item, vmDown.editorFocus.value, "10.0")
            repeat(10) { runCurrent() }

            val positionsDown = vmDown.inputPositions.value.filterIsInstance<PositionLine>()
            assertEquals(5, positionsDown.size)
            // Line 1 is 5.0 (setavg 10)
            // Edited line (setavg 20) moved to 10.0 km and should be above lines with setavg 30 and 40 (which were below it)
            assertEquals(10.0, positionsDown[0].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(20.0, positionsDown[1].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(30.0, positionsDown[2].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(40.0, positionsDown[3].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(50.0, positionsDown[4].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(LineNumber(2, 0), vmDown.selectedLineIndex.value)
        } finally {
            vmDown.viewModelScope.cancel()
        }
    }

    @Test
    fun testPositionMovementViaKeyPress() = runTest(withMain.dispatcher) {
        val sec = db.createSection("TestSectionKeys", null) as SectionInsertOrRenameResult.Success
        db.updateSectionPositions(
            sec.section.id,
            """
                10.0 setavg 10
                10.0 setavg 20
                10.5 setavg 30
                10.0 setavg 40
            """.trimIndent()
        )

        val vm = LiveSectionViewModel(
            sec.section.id,
            db,
            defaultPreferencesMock(),
            withMain.dispatcher
        )

        try {
            repeat(10) { runCurrent() }
            vm.switchEditor()
            repeat(10) { runCurrent() }

            // Line 3 is at 10.5 km (setavg 30)
            vm.selectLine(LineNumber(3, 0), DataKind.Distance)
            repeat(10) { runCurrent() }

            // Delete '5' using DEL key -> becomes 10.0 km (moves UP from 10.5 to 10.0)
            vm.keyPress(GridKey.DEL)
            repeat(10) { runCurrent() }

            val positions = vm.inputPositions.value.filterIsInstance<PositionLine>()
            assertEquals(4, positions.size)
            // Lines 1 and 2 (10, 20) were above it -> edited item (30) is below them at index 2 (Line 3)
            // Line 4 (40) was below it -> edited item is above it
            assertEquals(10.0, positions[0].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(20.0, positions[1].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(30.0, positions[2].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(40.0, positions[3].modifier<PositionLineModifier.SetAvg>()?.setavg?.valueKmh)
            assertEquals(LineNumber(3, 0), vm.selectedLineIndex.value)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun testAddKeyInsertsPositionBelow() = runTest(withMain.dispatcher) {
        val sec = db.createSection("TestSectionAdd", null) as SectionInsertOrRenameResult.Success
        db.updateSectionPositions(
            sec.section.id,
            """
                1.0 setavg 50
                3.0 setavg 60
            """.trimIndent()
        )

        val viewModel = LiveSectionViewModel(
            sec.section.id,
            db,
            defaultPreferencesMock(),
            withMain.dispatcher
        )

        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }

            // Select Line 1 (1.0 km)
            viewModel.selectLine(LineNumber(1, 0), DataKind.Distance)
            repeat(10) { runCurrent() }

            // Press ADD key
            viewModel.keyPress(GridKey.ADD)
            repeat(10) { runCurrent() }

            // There should now be 3 items: 1.0 km, 1.0 km (inserted at line 2 with the distance of line 1), and 3.0 km (now line 3)
            val positions = viewModel.inputPositions.value.filterIsInstance<PositionLine>()
            assertEquals(3, positions.size)
            assertEquals(1.0, positions[0].atKm.valueKm)
            assertEquals(1.0, positions[1].atKm.valueKm)
            assertEquals(3.0, positions[2].atKm.valueKm)

            // The selected line should be the new line (Line 2) with the whole Distance field selected
            assertEquals(LineNumber(2, 0), viewModel.selectedLineIndex.value)
            assertEquals(DataKind.Distance, viewModel.editorFocus.value.kind)
            assertTrue(viewModel.editorFocus.value.selectAll)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun addItemAfterLine(lineNumber: Int, block: suspend (LiveSectionViewModel) -> Unit) = runTest(withMain.dispatcher) {
        val sec = db.createSection("TestSectionAddSelected$lineNumber", null) as SectionInsertOrRenameResult.Success
        db.updateSectionPositions(
            sec.section.id,
            """
                1.0 setavg 50
                3.0 setavg 60
                5.0 endavg
            """.trimIndent()
        )

        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock(), withMain.dispatcher)
        try {
            repeat(10) { runCurrent() }
            viewModel.switchEditor()
            repeat(10) { runCurrent() }
            viewModel.selectLine(LineNumber(lineNumber, 0), DataKind.Distance)
            repeat(10) { runCurrent() }
            viewModel.keyPress(GridKey.ADD)
            repeat(10) { runCurrent() }
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun LiveSectionViewModel.distances() =
        inputPositions.value.filterIsInstance<PositionLine>().map { it.atKm.valueKm }

    private fun LiveSectionViewModel.selectedDistanceText() =
        editorFocus.value.rawText ?: inputPositions.value.filterIsInstance<PositionLine>()
            .single { it.lineNumber == selectedLineIndex.value }.atKm.valueKm.toString()

    @Test
    fun testDigitAfterAddReplacesSelectedDistance() = addItemAfterLine(1) { viewModel ->
        viewModel.keyPress(GridKey.N_2)
        assertEquals(listOf(1.0, 2.0, 3.0, 5.0), viewModel.distances())
        assertEquals(LineNumber(2, 0), viewModel.selectedLineIndex.value)
        assertEquals("2.0", viewModel.selectedDistanceText())
        assertEquals(1, viewModel.editorFocus.value.cursor)
        assertFalse(viewModel.editorFocus.value.selectAll)

        // Further digits extend the integral part
        viewModel.keyPress(GridKey.N_4)
        assertEquals(listOf(1.0, 3.0, 5.0, 24.0), viewModel.distances())
        assertEquals(LineNumber(4, 0), viewModel.selectedLineIndex.value)
    }

    @Test
    fun testDigitAfterAddMovesItemUp() = addItemAfterLine(3) { viewModel ->
        assertEquals(listOf(1.0, 3.0, 5.0, 5.0), viewModel.distances())
        viewModel.keyPress(GridKey.N_2)
        assertEquals(listOf(1.0, 2.0, 3.0, 5.0), viewModel.distances())
        assertEquals(LineNumber(2, 0), viewModel.selectedLineIndex.value)
    }

    @Test
    fun testDotAfterAddStartsFraction() = addItemAfterLine(1) { viewModel ->
        viewModel.keyPress(GridKey.DOT)
        assertEquals("0.0", viewModel.selectedDistanceText())
        assertEquals(2, viewModel.editorFocus.value.cursor)
        viewModel.keyPress(GridKey.N_3)
        assertEquals("0.3", viewModel.selectedDistanceText())
        assertEquals(listOf(0.3, 1.0, 3.0, 5.0), viewModel.distances())
    }

    @Test
    fun testDeleteAfterAddClearsDistance() = addItemAfterLine(2) { viewModel ->
        viewModel.keyPress(GridKey.DEL)
        assertEquals("0.0", viewModel.selectedDistanceText())
        assertEquals(1, viewModel.editorFocus.value.cursor)
        assertFalse(viewModel.editorFocus.value.selectAll)
        assertEquals(listOf(0.0, 1.0, 3.0, 5.0), viewModel.distances())
    }

    @Test
    fun testArrowAfterAddKeepsDistanceAndDropsSelection() = addItemAfterLine(2) { viewModel ->
        val cursor = viewModel.editorFocus.value.cursor
        viewModel.keyPress(GridKey.RIGHT)
        assertFalse(viewModel.editorFocus.value.selectAll)
        assertEquals(cursor, viewModel.editorFocus.value.cursor)
        assertEquals(listOf(1.0, 3.0, 3.0, 5.0), viewModel.distances())

        // Now typing edits at the cursor instead of replacing the text
        viewModel.keyPress(GridKey.N_1)
        assertEquals("3.01", viewModel.selectedDistanceText())
    }
}
