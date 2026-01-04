package com.h0tk3y.rally.android.scenes

import app.cash.turbine.turbineScope
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.racecervice.PrimaryRaceService
import com.h0tk3y.rally.android.testing.MainDispatcherRule
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import defaultPreferencesMock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

@ExperimentalCoroutinesApi
class CurrentRaceLineUpdateTest {
    @get:Rule
    val withMain = MainDispatcherRule()

    @Test
    fun `current race line is updated on distance updates from the service`() = runTest {
        val db = InMemoryDatabaseOperations()
        val sec = db.createSection("test", simplePositions(0.0, 100.0, 100)) as SectionInsertOrRenameResult.Success

        val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock())
        val raceModelAtStart = raceModelOfDistance(0.0)
        val raceStateFlow = MutableStateFlow(RaceState.InRace(sec.section.id, raceModelAtStart, null, null, raceModelAtStart))
        
        val service = mockk<PrimaryRaceService>(
            relaxed = true // chain-mock the other flows; no-op for service state updates from the view model
        ) {
            every { raceState }.returns(raceStateFlow)
        }

        turbineScope {
            val raceState = viewModel.raceState.testIn(backgroundScope)
            val raceCurrentLineIndex = viewModel.raceCurrentLineIndex.testIn(backgroundScope)
            
            assertIs<RaceUiState.NoRaceServiceConnection>(raceState.awaitItem())
            assertNull(raceCurrentLineIndex.awaitItem())

            viewModel.onServiceConnected(service)
            assertEquals(raceModelOfDistance(0.0), assertIs<RaceUiState.RaceGoing>(raceState.awaitItem()).raceModel)
            assertEquals(LineNumber(1, 0), raceCurrentLineIndex.awaitItem())

            raceStateFlow.emit(RaceState.InRace(sec.section.id, raceModelOfDistance(1.0), null, null, raceModelAtStart))
            assertEquals(raceModelOfDistance(1.0), assertIs<RaceUiState.RaceGoing>(raceState.awaitItem()).raceModel)
            assertEquals(viewModel.preprocessedPositions.value[1].lineNumber, raceCurrentLineIndex.awaitItem())

            // Also works with rounding to 3 digits: 1.9999... -> 2.0, and current line is updated
            raceStateFlow.emit(RaceState.InRace(sec.section.id, raceModelOfDistance(1.9999), null, null, raceModelAtStart))
            assertEquals(raceModelOfDistance(1.9999), assertIs<RaceUiState.RaceGoing>(raceState.awaitItem()).raceModel)
            assertEquals(viewModel.preprocessedPositions.value[2].lineNumber, raceCurrentLineIndex.awaitItem())

            // Going backwards works as well: 1.999 -> 1.5
            raceStateFlow.emit(RaceState.InRace(sec.section.id, raceModelOfDistance(1.5), null, null, raceModelAtStart))
            assertEquals(raceModelOfDistance(1.5), assertIs<RaceUiState.RaceGoing>(raceState.awaitItem()).raceModel)
            assertEquals(viewModel.preprocessedPositions.value[1].lineNumber, raceCurrentLineIndex.awaitItem())

            viewModel.viewModelScope.cancel()
        }
    }
}


private fun simplePositions(from: Double, to: Double, n: Int) = buildString {
    appendLine("$from setavg 60.0")
    val step = (to - from) / n
    generateSequence(1, Int::inc).map { it * step }.takeWhile { it < to }.forEach {
        appendLine(it)
    }
    appendLine(to)
}

private fun raceModelOfDistance(currentDistance: Double) = RaceModel(
    Instant.DISTANT_PAST,
    DistanceKm.zero,
    DistanceKm(currentDistance),
    DistanceKm.zero,
    distanceGoingUp = true,
    SpeedKmh(10.0)
)