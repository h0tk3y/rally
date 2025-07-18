package com.h0tk3y.rally

import kotlin.test.Test
import kotlin.test.assertIs
import com.h0tk3y.rally.IntervalBuilder.BuildIntervalResult.Failure
import com.h0tk3y.rally.IntervalBuilder.BuildIntervalResult.Result
import kotlin.test.assertEquals
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
    
    private inline fun <reified T> failsWithReason(result: IntervalBuilder.BuildIntervalResult) {
        assertIs<Failure>(result)
        assertIs<T>(result.failure.reason)
    } 
    
}