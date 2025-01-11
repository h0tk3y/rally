package com.h0tk3y.rally

import java.io.Reader

class InputRoadmapParser(private val modifiersValidator: ModifiersValidator) {
    fun parseRoadmap(input: Reader): List<RoadmapInputLine> {
        return input.buffered().lineSequence().withIndex().map { (index, value) ->
            parseLine(value, LineNumber(index + 1, 0))
        }.toList()
    }

    private fun parseLine(line: String, lineNumber: LineNumber): RoadmapInputLine {
        if (line.startsWith(CommentLine.COMMENT_PREFIX)) {
            return CommentLine(line.removePrefix(CommentLine.COMMENT_PREFIX).trim(), lineNumber)
        }
        val parts = line.trim().split(" ")
        val dst = parts.first().toDoubleOrNull() ?: error("failed to parse dst in line $lineNumber: $line")
        val d = DistanceKm(dst)

        val modifiers = buildList {
            var currentIndex = 1
            while (currentIndex <= parts.lastIndex) {
                val sublist = parts.subList(currentIndex, parts.size)
                val result = parsers.asSequence()
                    .mapNotNull { parser -> parser.parse(sublist).takeIf { result -> result.modifier != null } }
                    .firstOrNull()
                if (result == null)
                    error("failed to parse line modifiers in line $lineNumber: $line")
                else {
                    add(checkNotNull(result.modifier))
                    currentIndex += result.consumed
                }
            }
        }
        val validateModifiersProblemFound = modifiersValidator.validateModifiers(modifiers)
        if (validateModifiersProblemFound != null) {
            error("invalid modifiers: ${validateModifiersProblemFound.message} in line $lineNumber: $line")
        }

        return PositionLine(d, lineNumber, modifiers)
    }

    private data class ModifierParseResult<out T : PositionLineModifier>(val modifier: T?, val consumed: Int) {
        companion object {
            val None = ModifierParseResult(null, 0)
        }
    }

    private interface ModifierParser<T : PositionLineModifier> {
        fun parse(parts: List<String>): ModifierParseResult<T>
    }

    private class ByWord<T : PositionLineModifier>(
        private val word: String,
        private val consumeParts: Int,
        private val partsAreNumbers: Boolean = true,
        private val buildModifier: (List<String>) -> T?
    ) : ModifierParser<T> {
        override fun parse(parts: List<String>): ModifierParseResult<T> =
            when {
                parts[0] != word -> ModifierParseResult.None
                parts.size - 1 < consumeParts -> ModifierParseResult.None
                else -> {
                    val subList = parts.subList(1, parts.size)
                    if (partsAreNumbers && subList.take(consumeParts).any { it.toDoubleOrNull() == null }) {
                        ModifierParseResult.None
                    } else {
                        val result = ModifierParseResult(buildModifier(subList), consumeParts + 1)
                        result.takeIf { it.modifier != null } ?: ModifierParseResult.None
                    }
                }
            }
    }

    private val parsers = listOf<ModifierParser<*>>(
        ByWord("odo", 1) { (arg) -> PositionLineModifier.OdoDistance(DistanceKm(arg.toDouble())) },
        ByWord("setavg", 1) { (arg) -> PositionLineModifier.SetAvgSpeed(parseAvgSpeed(arg)) },
        ByWord("endavg", 1) { (arg) -> PositionLineModifier.EndAvgSpeed(parseAvgSpeed(arg)) },
        ByWord("endavg", 0) { (_) -> PositionLineModifier.EndAvgSpeed(null) },
        ByWord("thenavg", 1) { (arg) -> PositionLineModifier.ThenAvgSpeed(parseAvgSpeed(arg)) },
        ByWord("atime", 1, partsAreNumbers = false) { (arg) -> PositionLineModifier.AstroTime(TimeOfDay.parse(arg)) },
        ByWord("here", 1) { (arg) ->
            PositionLineModifier.Here(TimeMinSec.parse(arg)?.toHr() ?: error("failed to parse time"))
        },
        ByWord("s", 2) { (distance, count) -> 
            val dst = distance.toDoubleOrNull() ?: error("failed to parse distance for synthetics")
            val cnt = count.toIntOrNull() ?: error("failed to parse count for synthetics")
            PositionLineModifier.AddSynthetic(DistanceKm(dst), cnt)
        },
        ByWord("calc", 0) { PositionLineModifier.CalculateAverage },
        ByWord("endcalc", 0) { PositionLineModifier.EndCalculateAverage }
    )
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

        s.toDoubleOrNull() != null ->
            parseAvgSpeed(s + SpeedKmh.KMH_SUFFIX)

        else -> SpeedKmh.parse(s) ?: error("failed to parse avgspd notation $string")
    }
}