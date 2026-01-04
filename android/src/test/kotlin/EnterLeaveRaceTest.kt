package com.h0tk3y.rally.android.scenes

import app.cash.turbine.turbineScope
import com.h0tk3y.rally.CommentLine
import com.h0tk3y.rally.DefaultModifierValidator
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.InputRoadmapParser
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.RoadmapInputLine
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.racecervice.PrimaryRaceService
import com.h0tk3y.rally.android.testing.MainDispatcherRule
import com.h0tk3y.rally.android.testing.simplePositions
import com.h0tk3y.rally.android.views.StartOption
import com.h0tk3y.rally.model.RaceEventKind
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import com.h0tk3y.rally.modifier
import defaultPreferencesMock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Ignore
@ExperimentalCoroutinesApi
class EnterLeaveRaceTest {
    @get:Rule
    val withMain = MainDispatcherRule()

    val db = InMemoryDatabaseOperations()
    val sec = db.createSection("test", simplePositions(0.0, 3.0, 3)) as SectionInsertOrRenameResult.Success
    val initial = InputRoadmapParser(DefaultModifierValidator()).parseRoadmap(sec.section.serializedPositions.reader()).filterIsInstance<PositionLine>()

    val viewModel = LiveSectionViewModel(sec.section.id, db, defaultPreferencesMock())
    val raceModelAtStart = raceModelOfDistance(0.0)
    val raceStateFlow = MutableStateFlow<RaceState>(RaceState.Going(sec.section.id, raceModelAtStart, null, null))

    val service = mockk<PrimaryRaceService>(
        relaxed = true // chain-mock the other flows; no-op for service state updates from the view model
    ) {
        every { raceState }.returns(raceStateFlow)
    }

    interface PositionChangeContext : CoroutineScope {
        suspend fun positionsChanged()
    }

    private fun testPositions(
        runActions: suspend PositionChangeContext.() -> Unit,
        checkPositions: (List<PositionLine>) -> Unit
    ) = runTest {
        turbineScope {
            val positions = viewModel.preprocessedPositions.testIn(backgroundScope)
            positions.awaitItem()

            viewModel.onServiceConnected(service)

            runActions(object : PositionChangeContext, CoroutineScope by this {
                override suspend fun positionsChanged() {
                    println(positions.awaitItem())
                }
            })
            val positionsAfter = positions.awaitItem()
            positions.ensureAllEventsConsumed()
            checkPositions(positionsAfter.filterIsInstance<PositionLine>())
        }
    }

    @Test
    fun `start timed race at zero from current state`() = testPositions(
        runActions = {
            viewModel.startRace(StartOption(StartOption.StartNowFromGoingState, isRace = true))
        },
        checkPositions = { result ->
            assertEquals(initial.withoutLineNumbers(), result.toList().filterIndexed { index, _ -> index != 1 }.withoutLineNumbers())
            assertIs<PositionLine>(result[1]).run {
                assertEquals(DistanceKm(0.0), atKm)
                assertNotNull(modifier<PositionLineModifier.SetAvgSpeed>())
                assertNotNull(modifier<PositionLineModifier.AstroTime>())
            }
        }
    )

    @Test
    fun `start timed race by position that has no setavg`() = testPositions(
        runActions = {
            raceStateFlow.emit(RaceState.Going(sec.section.id, raceModelOfDistance(1.0), null, null))
            viewModel.selectLine(LineNumber(2, 0), null)
            viewModel.startRace(StartOption(StartOption.StartNowFromGoingState, isRace = true))
        },
        checkPositions = { result ->
            assertEquals(initial.withoutIndices(1).withoutLineNumbers(), result.withoutIndices(1).withoutLineNumbers())
            assertIs<PositionLine>(result[1]).run {
                assertEquals(DistanceKm(1.0), atKm)
                assertNotNull(modifier<PositionLineModifier.SetAvgSpeed>())
                assertNotNull(modifier<PositionLineModifier.AstroTime>())
            }
            db.events.value.last { it.sectionId == sec.section.id }.run {
                assertEquals(RaceEventKind.RACE_START, kind)
                assertEquals(1.0, distance)
            }
        }
    )

    @Test
    fun `finish at an existing position with endavg`() {
        testPositions(
            runActions = {
                val before = Clock.System.now()
                val now = before + 1.seconds

                val going = RaceState.Going(sec.section.id, raceModelOfDistance(2.0, before), null, null)
                raceStateFlow.emit(going)

                viewModel.startRace(StartOption(StartOption.StartNowFromGoingState, isRace = true))
                positionsChanged()

                raceStateFlow.emit(RaceState.InRace(sec.section.id, raceModelOfDistance(3.0, now), null, null, going.raceModel))

                viewModel.finishRace()
                positionsChanged()
                positionsChanged()
                positionsChanged()
            },
            checkPositions = { result ->
                assertEquals(initial.size + 1, result.size)
                assertNotNull(result.last().modifier<PositionLineModifier.EndAvg>())
                assertNotNull(result.dropLast(1).last().modifier<PositionLineModifier.EndAvg>())
            }
        )
    }
    
    @Ignore
    @Test
    fun `finish at an existing position with setavg?`() {
        // TODO
    }

    @Ignore
    @Test
    fun `start before the first position in the roadmap`() {
        // TODO
    }

    @Ignore
    @Test
    fun `start after the last position in the roadmap`() {
        // TODO
    }

    @Ignore
    @Test
    fun `finish at a lower distance than the start was`() {
        // TODO
    }

    @Ignore
    @Test
    fun `removing speed limits while going backward`() {
        // TODO
    }
}

private fun <T> List<T>.withoutIndices(vararg indices: Int) =
    buildList {
        val indexSet = indices.toHashSet()
        this@withoutIndices.forEachIndexed { index, value -> if (index !in indexSet) add(value) }
    }

private fun List<RoadmapInputLine>.withoutLineNumbers(): List<RoadmapInputLine> = map {
    when (it) {
        is CommentLine -> it.copy(lineNumber = LineNumber(1, 0))
        is PositionLine -> it.copy(lineNumber = LineNumber(1, 0))
    }
}

private fun raceModelOfDistance(currentDistance: Double, startAt: Instant = Instant.DISTANT_PAST) = RaceModel(
    startAt,
    DistanceKm.zero,
    DistanceKm(currentDistance),
    DistanceKm.zero,
    distanceGoingUp = true,
    SpeedKmh(10.0)
)
