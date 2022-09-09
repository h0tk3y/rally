package com.h0tk3y.rally

import kotlin.math.roundToInt

class ResultsFormatter {
    fun formatResults(
        input: Iterable<RoadmapInputLine>,
        result: RallyTimesResult,
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
                    appendLine("${CommentLine.commentPrefix} ${line.commentText}")
                }
            }
        }
    }

    private fun formatPositionLine(
        line: PositionLine,
        result: RallyTimesResult,
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
            append(
                line.modifiers.joinToString(" ", transform = ::formatModifier)
                    .let { str -> if (str.isNotEmpty()) " $str" else str })
        }


        val goAtAvgSpd = result.goAtAvgSpeed[line.lineNumber]
        if (goAtAvgSpd != null && goAtAvgSpd != line.modifier<PositionLineModifier.SetAvg>()?.setavg) {
            append(" – go at $goAtAvgSpd")
        }
        if (average != null) {
            append(" - avg $average")
        }
    }

    private fun formatModifier(modifier: PositionLineModifier): String = when (modifier) {
        is PositionLineModifier.SetAvgSpeed -> "setavg ${modifier.setavg}"
        is PositionLineModifier.EndAvgSpeed -> "endavg ${modifier.endavg}"
        is PositionLineModifier.ThenAvgSpeed -> "thenavg ${modifier.setavg}"
        is PositionLineModifier.Here -> "here ${modifier.atTime.toMinSec()}"
        PositionLineModifier.IsSynthetic -> "(S)"
        is PositionLineModifier.AddSynthetic -> ""
        is PositionLineModifier.CalculateAverage -> "- avg below"
        is PositionLineModifier.EndCalculateAverage -> ""
    }
}

private fun Double.strRound3(): String =
    ((this * 1000).roundToInt() / 1000.0).toString()