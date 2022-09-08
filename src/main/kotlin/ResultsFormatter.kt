package com.h0tk3y.rally

import kotlin.math.roundToInt

class ResultsFormatter {
    fun formatResults(
        input: Iterable<RoadmapInputLine>,
        result: RallyTimesResult,
        calibrationCoefficient: Double?
    ): String = buildString {
        input.forEach { line ->
            when (line) {
                is PositionLine -> {
                    appendLine(formatPositionLine(line, result, calibrationCoefficient))
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
        calibrationCoefficient: Double?
    ): String = buildString {
        val atKm = line.atKm
        val times = result.timeVectorsAtRoadmapLine.getValue(line.lineNumber)
        append(atKm.valueKm.strRound3().padEnd(7, ' '))
        if (calibrationCoefficient != null) {
            append(" (")
            append((atKm.valueKm * calibrationCoefficient).strRound3().plus(")").padEnd(9, ' '))
        }

        append(times.outer.toMinSec().toString().padEnd(5, ' '))
        if (times.values.size > 1) {
            append(times.values.dropLast(1).reversed().joinToString(", ", " (", ")") { it.toMinSec().toString() })
        }

        when (line) {
            is PositionLine.SetAvgSpeed -> append(" - setavg ${line.avgspeed}")
            is PositionLine.EndAvgSpeed -> append(" - endavg ${line.avgspeed}")
            else -> Unit
        }
        val goAtAvgSpd = result.goAtAvgSpeed[line.lineNumber]
        if (goAtAvgSpd != null) {
            append(" – go at $goAtAvgSpd")
        }
    }
}

private fun Double.strRound3(): String =
    ((this * 1000).roundToInt() / 1000.0).toString()