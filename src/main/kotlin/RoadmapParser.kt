package com.h0tk3y.rally

import java.io.InputStreamReader

interface ModifiersValidator {
    data class Problem(val message: String?)

    fun validateModifiers(modifiers: Collection<PositionLineModifier>): Problem?
}

class InputRoadmapParser(val modifiersValidator: ModifiersValidator) {
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
        private val buildModifier: (List<String>) -> T?
    ) : ModifierParser<T> {
        override fun parse(parts: List<String>): ModifierParseResult<T> =
            when {
                parts[0] != word -> ModifierParseResult.None
                parts.size - 1 < consumeParts -> ModifierParseResult.None
                else -> {
                    val result = ModifierParseResult(buildModifier(parts.subList(1, parts.size)), consumeParts + 1)
                    result.takeIf { it.modifier != null } ?: ModifierParseResult.None
                }
            }
    }

    private val parsers = listOf<ModifierParser<*>>(
        ByWord("setavg", 1) { (arg) -> PositionLineModifier.SetAvgSpeed(parseAvgSpeed(arg)) },
        ByWord("endavg", 1) { (arg) -> PositionLineModifier.EndAvgSpeed(parseAvgSpeed(arg)) },
        ByWord("thenavg", 1) { (arg) -> PositionLineModifier.ThenAvgSpeed(parseAvgSpeed(arg)) }
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

        s.endsWith(".") && s.removeSuffix(".").toDoubleOrNull() != null ->
            parseAvgSpeed(s.removeSuffix(".") + SpeedKmh.kmhSuffix)

        else -> SpeedKmh.parse(s) ?: error("failed to parse avgspd notation $string")
    }
}

class DefaultModifierValidator : ModifiersValidator {
    override fun validateModifiers(modifiers: Collection<PositionLineModifier>): ModifiersValidator.Problem? {
        val avg = modifiers.filter { it is PositionLineModifier.SetAvg || it is PositionLineModifier.EndAvg }
        if (avg.size > 1) {
            return ModifiersValidator.Problem("more than one setavg/endavg modifiers found")
        }
        return null
    }
}