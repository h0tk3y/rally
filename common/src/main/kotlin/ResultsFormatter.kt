package com.h0tk3y.rally

import kotlin.math.roundToInt

class ResultsFormatter {
    fun formatResults(
        input: Iterable<RoadmapInputLine>,
        result: RallyTimesResultSuccess,
        calculatedAverages: Map<LineNumber, SpeedKmh>,
        calibrationCoefficient: Double?
    ): String = buildString {
        input.forEach { line ->
            when (line) {
                is PositionLine -> {
                    appendLine(
                        formatPositionLine(
                            line,
                            result,
                            calibrationCoefficient,
                            calculatedAverages[line.lineNumber]
                        )
                    )
                } 
                is CommentLine -> {
                    appendLine("${CommentLine.COMMENT_PREFIX} ${line.commentText}")
                }
            }
        }
    }

    fun formatFailures(
        result: RallyTimesResultFailure
    ): String {
        return result.failures.joinToString("\n") { formatFailureLine(it) }
    }

    private fun formatFailureLine(
        failure: CalculationFailure
    ): String {
        return "" + failure.line.lineNumber + " " + when (failure.reason) {
            is FailureReason.DistanceIsNotIncreasing -> "distance is not increasing"
            is FailureReason.NonMatchingAverageEnd -> "endavg speed does not match the set average"
            is FailureReason.AverageSpeedUnknown -> "position out of any interval with known speed"
            is FailureReason.UnexpectedAverageEnd -> "average ending found but no setavg"
            is FailureReason.OuterIntervalNotCovered -> "outer interval not covered by subs"
        }
    }

    private fun formatPositionLine(
        line: PositionLine,
        result: RallyTimesResultSuccess,
        calibrationCoefficient: Double?,
        average: SpeedKmh?
    ): String = buildString {
        val atKm = line.atKm
        val times = result.timeVectorsAtRoadmapLine[line.lineNumber] ?: TimeHrVector.of(TimeHr.zero)
        append(atKm.valueKm.strRound3().padEnd(7, ' '))
        if (calibrationCoefficient != null) {
            append(" (")
            append((atKm.valueKm * calibrationCoefficient).strRound3().plus(")").padEnd(9, ' '))
        }

        append(times.outer.toMinSec().toString().padEnd(5, ' '))
        if (times.values.size > 1) {
            append(times.values.dropLast(1).reversed().joinToString(", ", " (", ")") { it.toMinSec().toString() })
        }

        if (line.modifiers.isNotEmpty()) {
            append(modifiersString(line))
        }


        val goAtAvgSpd = result.goAtAvgSpeed[line.lineNumber]
        if (goAtAvgSpd != null && goAtAvgSpd != line.modifier<PositionLineModifier.SetAvg>()?.setavg) {
            append(" – go at $goAtAvgSpd")
        }
        if (average != null) {
            append(" - avg $average")
        }
    }

    fun modifiersString(line: PositionLine): String =
        line.modifiers.joinToString(" ", transform = ::formatModifier)
            .let { str -> if (str.isNotEmpty()) " $str" else str }

    private fun formatModifier(modifier: PositionLineModifier): String = when (modifier) {
        is PositionLineModifier.OdoDistance -> "odo ${modifier.distanceKm.valueKm.strRound3()}"
        is PositionLineModifier.SetAvgSpeed -> "setavg ${modifier.setavg}"
        is PositionLineModifier.EndAvgSpeed -> "endavg ${modifier.endavg?.toString() ?: ""}"
        is PositionLineModifier.ThenAvgSpeed -> "thenavg ${modifier.setavg}"
        is PositionLineModifier.Here -> "here ${modifier.atTime.toMinSec()}"
        is PositionLineModifier.AstroTime -> "atime ${modifier.timeOfDay.timeStrNoDayOverflow()}"
        PositionLineModifier.IsSynthetic -> "(S)"
        is PositionLineModifier.AddSynthetic -> ""
        is PositionLineModifier.CalculateAverage -> "- avg below"
        is PositionLineModifier.EndCalculateAverage -> ""
    }
}

fun Double.strRound3(): String = when (this) {
    Double.POSITIVE_INFINITY -> "∞"
    Double.NEGATIVE_INFINITY -> "-∞"
    else -> ((this * 1000).roundToInt() / 1000.0).toString()
}

fun Double.strRound1(): String = when (this) {
    Double.POSITIVE_INFINITY -> "∞"
    Double.NEGATIVE_INFINITY -> "-∞"
    else -> ((this * 10).roundToInt() / 10.0).toString() 
}
