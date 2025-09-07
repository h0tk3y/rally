package com.h0tk3y.rally

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RallyTimesIntervalsCalculatorTest {
    @Test
    fun `when no atime is given, returns deltas but no atimes for the outer interval`() {
        val roadmap = parsePositions(
            """
            0.0 setavg 60 
            90.0
            """.trimIndent()
        )

        val result = assertIs<RallyTimesResultSuccess>(RallyTimesIntervalsCalculator().rallyTimes(roadmap))

        assertEquals(
            listOf(TimeHr.zero, TimeHr.byMoving(DistanceKm(90.0), SpeedKmh(60.0))),
            result.timesForOuterInterval.values.map { it.outer }
        )

        assertTrue(result.astroTimeAtRoadmapLineForOuter.isEmpty())
    }

    @Test
    fun `pure-outer atimes get correctly calculated from roadmap with atime at start`() {
        val roadmap = parsePositions(
            """
            0.0 setavg 60 atime 10:00:00
            30.0
            90.0
            """.trimIndent()
        )

        val result = assertIs<RallyTimesResultSuccess>(RallyTimesIntervalsCalculator().rallyTimes(roadmap))

        assertEquals(
            listOf(TimeHr.zero, TimeHr.byMoving(DistanceKm(30.0), SpeedKmh(60.0)), TimeHr.byMoving(DistanceKm(90.0), SpeedKmh(60.0))),
            result.timesForOuterInterval.values.map { it.outer }
        )

        assertEquals(
            listOfNotNull(TimeDayHrMinSec.tryParse("10:00:00"), TimeDayHrMinSec.tryParse("10:30:00"), TimeDayHrMinSec.tryParse("11:30:00")),
            result.astroTimeAtRoadmapLineForOuter.values.toList()
        )
    }

    @Test
    fun `pure-outer atimes get correctly calculated from roadmap with atime in the middle`() {
        val roadmap = parsePositions(
            """
            0.0 setavg 60 
            30.0 atime 10:00:00
            90.0
            """.trimIndent()
        )

        val result = assertIs<RallyTimesResultSuccess>(RallyTimesIntervalsCalculator().rallyTimes(roadmap))

        assertEquals(
            listOf(TimeHr.zero, TimeHr.byMoving(DistanceKm(30.0), SpeedKmh(60.0)), TimeHr.byMoving(DistanceKm(90.0), SpeedKmh(60.0))),
            result.timesForOuterInterval.values.map { it.outer }
        )

        assertEquals(
            listOfNotNull(TimeDayHrMinSec.tryParse("9:30:00"), TimeDayHrMinSec.tryParse("10:00:00"), TimeDayHrMinSec.tryParse("11:00:00")),
            result.astroTimeAtRoadmapLineForOuter.values.toList()
        )
    }

    @Test
    fun `pure-outer atimes get correctly calculated from roadmap with atime in the end`() {
        val roadmap = parsePositions(
            """
            0.0 setavg 60 
            30.0 
            90.0 atime 10:00:00
        """.trimIndent()
        )

        val result = assertIs<RallyTimesResultSuccess>(RallyTimesIntervalsCalculator().rallyTimes(roadmap))

        assertEquals(
            listOf(TimeHr.zero, TimeHr.byMoving(DistanceKm(30.0), SpeedKmh(60.0)), TimeHr.byMoving(DistanceKm(90.0), SpeedKmh(60.0))),
            result.timesForOuterInterval.values.map { it.outer }
        )

        assertEquals(
            listOfNotNull(TimeDayHrMinSec.tryParse("08:30:00"), TimeDayHrMinSec.tryParse("09:00:00"), TimeDayHrMinSec.tryParse("10:00:00")),
            result.astroTimeAtRoadmapLineForOuter.values.toList()
        )
    }
}

private fun parsePositions(roadmap: String): List<PositionLine> =
    InputRoadmapParser(DefaultModifierValidator()).parseRoadmap(roadmap.reader()).filterIsInstance<PositionLine>()