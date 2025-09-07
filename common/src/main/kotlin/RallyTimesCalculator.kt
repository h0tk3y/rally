package com.h0tk3y.rally

import com.h0tk3y.rally.PositionLineModifier.SetAvgSpeed

sealed interface RallyTimesResult

data class RallyTimesResultSuccess(
    val timeVectorsAtRoadmapLine: Map<LineNumber, TimeHrVector>,
    val timesForOuterInterval: Map<LineNumber, TimeHrVector>,
    val astroTimeAtRoadmapLine: Map<LineNumber, TimeDayHrMinSec>,
    val astroTimeAtRoadmapLineForOuter: Map<LineNumber, TimeDayHrMinSec>,
    val goAtAvgSpeed: Map<LineNumber, SpeedKmh?>,
    val warnings: List<CalculationWarning>,
    val raceTimeDistanceLocalizer: TimeDistanceLocalizer?,
    val sectionTimeDistanceLocalizer: TimeDistanceLocalizer?
) : RallyTimesResult

interface TimeDistanceLocalizer {
    val base: PositionLine
    fun getExpectedTimeForDistance(distanceKm: DistanceKm): TimeHr?
}

data class RallyTimesResultFailure(
    val failures: List<CalculationFailure>
) : RallyTimesResult

data class CalculationWarning(
    val line: RoadmapInputLine,
    val reason: WarningReason
)

sealed interface WarningReason {
    data class ImpossibleToGetInTime(val start: RoadmapInputLine, val takes: TimeHr, val available: TimeHr) : WarningReason
}

data class CalculationFailure(
    val line: RoadmapInputLine,
    val reason: FailureReason
)

sealed interface FailureReason {
    object AverageSpeedUnknown : FailureReason
    object UnexpectedAverageEnd : FailureReason
    object NonMatchingAverageEnd : FailureReason
    object OuterIntervalNotCovered : FailureReason
    data class DistanceIsNotIncreasing(val shouldBeAtLeast: DistanceKm) : FailureReason
}

interface RallyTimes {
    fun rallyTimes(roadmap: List<PositionLine>): RallyTimesResult
}

fun validateRoadmap(roadmap: Iterable<RoadmapInputLine>): RallyTimesResultFailure? {
    val failures = mutableListOf<CalculationFailure>()
    var max = Double.NEGATIVE_INFINITY
    roadmap.filterIsInstance<PositionLine>().forEach {
        if (it.atKm.valueKm < max - 0.0001) {
            failures.add(CalculationFailure(it, FailureReason.DistanceIsNotIncreasing(DistanceKm(max))))
        }
        max = maxOf(max, it.atKm.valueKm)
    }
    
    var depth = 0
    var distance = DistanceKm.zero
    roadmap.filterIsInstance<PositionLine>().forEach {
        if (it.isSetAvg) {
            depth++
        } else if (it.isEndAvg) {
            depth--
            if (depth < 0) {
                failures.add(CalculationFailure(it, FailureReason.UnexpectedAverageEnd))
            }
        } else if (depth <= 0 && !(it.atKm == distance && it.modifier<SetAvgSpeed>() != null)) {
            failures.add(CalculationFailure(it, FailureReason.AverageSpeedUnknown))
        }
        distance = it.atKm
    }
    
    return if (failures.isNotEmpty())
        RallyTimesResultFailure(failures)
    else null
}
