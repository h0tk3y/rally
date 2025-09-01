package com.h0tk3y.rally

import com.h0tk3y.rally.IntervalBuilder.BuildIntervalResult.Failure
import com.h0tk3y.rally.IntervalBuilder.BuildIntervalResult.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IntervalBuilderTest {
    @Test
    fun `empty roadmap gives interval builder failure`() {
        val result = IntervalBuilder().buildInterval(emptyList(), 0, 0)
        failsWithReason<FailureReason.AverageSpeedUnknown>(result)
    }

    @Test
    fun `a roadmap with one line and no average speed gives interval builder failure`() {
        val result = IntervalBuilder().buildInterval(listOf(PositionLine(DistanceKm(0.0), LineNumber(1, 0), emptyList())), 0, 0)
        failsWithReason<FailureReason.OuterIntervalNotCovered>(result)
    }

    @Test
    fun `a roadmap with one line and average speed set on it produces a valid empty interval`() {
        val result = IntervalBuilder().buildInterval(
            listOf(
                PositionLine(DistanceKm(0.0), LineNumber(1, 0), listOf(PositionLineModifier.SetAvgSpeed(SpeedKmh(60.0))))
            ), 0, 0
        )
        assertIs<Result>(result)
        assertTrue { result.interval.subIntervals.isEmpty() }
        assertEquals(DistanceKm.zero, result.interval.pureFragments.single().distance)
        assertEquals(SpeedKmh(60.0), result.interval.targetAverageSpeedKmh)
        assertEquals(result.interval.start, result.interval.end)
    }

    @Test
    fun `a roadmap with two lines and average speed produces one interval`() {
        val result = IntervalBuilder().buildInterval(
            listOf(
                PositionLine(DistanceKm(0.0), LineNumber(1, 0), listOf(PositionLineModifier.SetAvgSpeed(SpeedKmh(60.0)))),
                PositionLine(DistanceKm(1.0), LineNumber(2, 0), listOf(PositionLineModifier.EndAvgSpeed(null)))
            ), 0, 1
        )
        assertIs<Result>(result)
        assertTrue { result.interval.subIntervals.isEmpty() }
        assertEquals(DistanceKm(1.0), result.interval.pureFragments.single().distance)
        assertEquals(SpeedKmh(60.0), result.interval.targetAverageSpeedKmh)
    }

    @Test
    fun `a roadmap with endavg and setavg at matching distance is correctly handled`() {
        val roadmaps = listOf(
            listOf(
                PositionLine(DistanceKm(0.0), LineNumber(1, 0), listOf(PositionLineModifier.SetAvgSpeed(SpeedKmh(60.0)))),
                PositionLine(DistanceKm(1.0), LineNumber(2, 0), listOf(PositionLineModifier.EndAvgSpeed(null))),
                PositionLine(DistanceKm(1.0), LineNumber(3, 0), listOf(PositionLineModifier.SetAvgSpeed(SpeedKmh(90.0)))),
                PositionLine(DistanceKm(2.0), LineNumber(4, 0), listOf(PositionLineModifier.EndAvgSpeed(null))),
            ),
            listOf(
                PositionLine(DistanceKm(0.0), LineNumber(1, 0), listOf(PositionLineModifier.SetAvgSpeed(SpeedKmh(60.0)))),
                PositionLine(DistanceKm(1.0), LineNumber(2, 0), listOf(PositionLineModifier.EndAvgSpeed(null))),
                PositionLine(DistanceKm(1.0), LineNumber(3, 0), emptyList()),
                PositionLine(DistanceKm(1.0), LineNumber(4, 0), listOf(PositionLineModifier.SetAvgSpeed(SpeedKmh(90.0)))),
                PositionLine(DistanceKm(2.0), LineNumber(5, 0), listOf(PositionLineModifier.EndAvgSpeed(null))),
            ),
        )
        roadmaps.forEach { roadmap ->
            val result = IntervalBuilder().buildInterval(
                roadmap, 0, roadmap.lastIndex
            )
            assertIs<Result>(result)
            assertEquals(2, result.interval.subIntervals.size)
            assertTrue(result.interval.pureFragments.isEmpty())
            assertEquals(listOf(0.0..1.0, 1.0..2.0), result.interval.subIntervals.map { it.start.atKm.valueKm..it.end.atKm.valueKm })
        }
    }


    private inline fun <reified T> failsWithReason(result: IntervalBuilder.BuildIntervalResult) {
        assertIs<Failure>(result)
        assertIs<T>(result.failure.reason)
    }

}