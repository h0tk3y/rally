package com.h0tk3y.rally

import java.io.InputStreamReader

class InputRoadmapParser {
    fun parseRoadmap(input: InputStreamReader): Iterable<RoadmapInputLine> {
        return input.buffered().lineSequence().withIndex().map { (index, value) ->
            parseLine(value, LineNumber(index + 1))
        }.toList()
    }

    private fun parseLine(line: String, lineNumber: LineNumber): RoadmapInputLine {
        if (line.startsWith(CommentLine.commentPrefix)) {
            return CommentLine(line.removePrefix(CommentLine.commentPrefix).trim(), lineNumber)
        }
        val parts = line.trim().split(" ", limit = 3)
        val dst = parts.first().toDoubleOrNull() ?: error("failed to parse dst in line $lineNumber: $line")
        val d = DistanceKm(dst)
        return when (parts.getOrNull(1)) {
            "setavg" ->
                PositionLine.SetAvgSpeed(d, parseAvgSpeed(parts[2]), lineNumber)

            "endavg" ->
                PositionLine.EndAvgSpeed(d, parseAvgSpeed(parts[2]), lineNumber)

            null -> PositionLine.Mark(d, lineNumber)

            else -> error("failed to parse roadmap line $lineNumber")
        }
    }
}

private fun parseAvgSpeed(string: String): SpeedKmh {
    val s = string.replace("\\s+".toRegex(), "")
    return when {
        s.count { it == '/' } == 1 -> {
            val (dst, time) = s.split("/")
            val d = dst.toDoubleOrNull() ?: error("failed to parse distance in avgspd notation $string")
            val t = TimeMinSec.parse(time) ?: error("failed to parse time in avgspd notation $string")
            SpeedKmh.averageAt(DistanceKm(d), t.toHr())
        }

        s.endsWith(".") && s.removeSuffix(".").toDoubleOrNull() != null ->
            parseAvgSpeed(s.removeSuffix(".") + SpeedKmh.kmhSuffix)

        else -> SpeedKmh.parse(s) ?: error("failed to parse avgspd notation $string")
    }
}