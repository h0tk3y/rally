package com.h0tk3y.rally.android.scenes

import com.h0tk3y.rally.DefaultModifierValidator
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.InputRoadmapParser
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.RallyTimesResult
import com.h0tk3y.rally.RallyTimesResultSuccess
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.racecervice.PrimaryRaceService
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState
import com.h0tk3y.rally.android.testing.MainDispatcherRule
import com.h0tk3y.rally.android.views.StartOption
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import com.h0tk3y.rally.modifier
import defaultPreferencesMock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Checks that finishing a race leaves a roadmap that the app can still calculate.
 */
@ExperimentalCoroutinesApi
class FinishRaceTest {
    @get:Rule
    val withMain = MainDispatcherRule()

    private val sectionStart = Clock.System.now()
    private val raceStart = sectionStart + 60.seconds

    private class Outcome(val positions: List<PositionLine>, val results: RallyTimesResult)

    /**
     * Runs a section timer from 0.0, starts a race at the position on [raceStartLine], and moves the race to [finishAtKm].
     * Then finishes the race, and if [undoFinish] is set, undoes the finish.
     */
    private fun raceAndFinish(
        roadmap: String,
        raceStartLine: Int,
        finishAtKm: Double,
        duringRace: suspend LiveSectionViewModel.() -> Unit = { },
        undoFinish: Boolean = false
    ): Outcome {
        val db = InMemoryDatabaseOperations()
        val section = (db.createSection("test", roadmap) as SectionInsertOrRenameResult.Success).section
        val goingModel = raceModel(startAtKm = 0.0, currentKm = 0.0, sectionStart)
        val raceStateFlow = MutableStateFlow<RaceState>(RaceState.Going(section.id, goingModel, null, null))
        val service = mockk<PrimaryRaceService>(relaxed = true) {
            every { raceState }.returns(raceStateFlow)
            every { rememberSpeedLimit }.returns(MutableStateFlow(null))
            every { speedLimitPercent }.returns(MutableStateFlow(null))
            every { telemetryPublicState }.returns(MutableStateFlow(TelemetryPublicState.Simulation))
        }

        var outcome: Outcome? = null
        runTest(withMain.dispatcher) {
            val viewModel = LiveSectionViewModel(section.id, db, defaultPreferencesMock(), withMain.dispatcher)
            suspend fun step(action: suspend LiveSectionViewModel.() -> Unit) {
                action(viewModel)
                repeat(10) { runCurrent() }
            }
            try {
                repeat(10) { runCurrent() }
                viewModel.onServiceConnected(service)
                repeat(10) { runCurrent() }

                step { selectLine(LineNumber(raceStartLine, 0), null) }
                val raceStartKm = (viewModel.inputPositions.value[raceStartLine - 1] as PositionLine).atKm.valueKm
                step { startRace(StartOption(StartOption.StartAtSelectedPositionNow, isRace = true)) }
                step { raceStateFlow.emit(RaceState.InRace(section.id, raceModel(raceStartKm, raceStartKm, raceStart), null, null, goingModel)) }
                step(duringRace)
                val finishModel = raceModel(raceStartKm, finishAtKm, raceStart)
                step { raceStateFlow.emit(RaceState.InRace(section.id, finishModel, null, null, goingModel)) }
                step { finishRace() }
                if (undoFinish) {
                    step { raceStateFlow.emit(RaceState.Going(section.id, finishModel, finishModel, Clock.System.now())) }
                    step { undoFinishRace() }
                }

                outcome = Outcome(viewModel.inputPositions.value.filterIsInstance<PositionLine>(), viewModel.results.value)
            } finally {
                viewModel.viewModelScope.cancel()
                repeat(10) { runCurrent() }
            }
        }
        return outcome!!
    }

    private fun Outcome.assertCalculates() {
        assertIs<RallyTimesResultSuccess>(results, "the roadmap should still calculate, but got: $results\n${positions.joinToString("\n")}")
    }

    private fun Outcome.positionAt(km: Double): PositionLine =
        positions.last { it.atKm == DistanceKm(km) }

    @Test
    fun `finish after the roadmap ends the race stretch, with the outer stretch left open`() {
        val outcome = raceAndFinish(
            """
            0.0 setavg 60
            0.3 setavg 30
            0.6 endavg
            1.5
            """.trimIndent(),
            raceStartLine = 2,
            finishAtKm = 0.8
        )
        outcome.assertCalculates()
        assertNull(outcome.positionAt(0.8).modifier<PositionLineModifier.EndAvg>())
    }

    @Test
    fun `finish before the roadmap ends the race stretch, with the outer stretch left open`() {
        val outcome = raceAndFinish(
            """
            0.0 setavg 60
            0.3 setavg 30
            0.6 endavg
            1.5
            """.trimIndent(),
            raceStartLine = 2,
            finishAtKm = 0.5
        )
        outcome.assertCalculates()
    }

    @Test
    fun `finish ends the race stretch when the roadmap does not`() {
        val outcome = raceAndFinish(
            """
            0.0 setavg 60
            1.0
            2.0
            3.0
            """.trimIndent(),
            raceStartLine = 2,
            finishAtKm = 2.5
        )
        outcome.assertCalculates()
        assertNotNull(outcome.positionAt(2.5).modifier<PositionLineModifier.EndAvg>())
    }

    @Test
    fun `finish ends the race stretch after a speed change in the race`() {
        val outcome = raceAndFinish(
            """
            0.0 setavg 60
            1.0
            3.0 endavg
            """.trimIndent(),
            raceStartLine = 2,
            finishAtKm = 2.0,
            duringRace = { maybeCreateItemAtDistance(DistanceKm(1.5), true, listOf(PositionLineModifier.ThenAvgSpeed(SpeedKmh(40.0)))) }
        )
        outcome.assertCalculates()
        assertNotNull(outcome.positionAt(2.0).modifier<PositionLineModifier.EndAvg>())
    }

    @Test
    fun `finish at a lower distance than the race start`() {
        val outcome = raceAndFinish(
            """
            0.0 setavg 60
            1.0
            2.0
            3.0 endavg
            """.trimIndent(),
            raceStartLine = 3,
            finishAtKm = 1.5
        )
        outcome.assertCalculates()
    }

    @Test
    fun `undo finish removes the position that the finish added, also without endavg`() {
        val roadmap = """
            0.0 setavg 60
            0.3 setavg 30
            0.6 endavg
            1.5
            """.trimIndent()
        val parser = InputRoadmapParser(DefaultModifierValidator())
        val outcome = raceAndFinish(roadmap, raceStartLine = 2, finishAtKm = 0.8, undoFinish = true)
        assertEquals(
            parser.parseRoadmap(roadmap.reader()).filterIsInstance<PositionLine>().map { it.atKm },
            outcome.positions.map { it.atKm }
        )
    }
}

private fun raceModel(startAtKm: Double, currentKm: Double, startAt: Instant) = RaceModel(
    startAt,
    DistanceKm(startAtKm),
    DistanceKm(currentKm),
    DistanceKm.zero,
    distanceGoingUp = true,
    SpeedKmh(10.0)
)
