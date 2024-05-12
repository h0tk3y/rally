package com.h0tk3y.rally

import java.util.Stack

class SubsMatcher {
    fun matchSubs(roadmap: List<PositionLine>): SubsMatch {
        val stack = Stack<PositionLine>()
        val subNumbers = mutableMapOf<PositionLine, Int>()
        val matching = mutableMapOf<PositionLine, PositionLine>()

        var next = 0

        roadmap.forEach { line ->
            if (line.modifier<PositionLineModifier.SetAvgSpeed>() != null || stack.isEmpty() && line.isSetAvg) {
                stack.push(line)
                subNumbers[line] = next++
            }
            if (line.modifier<PositionLineModifier.ThenAvgSpeed>() != null) {
                if (stack.isNotEmpty()) {
                    updateBy(stack, matching, line, subNumbers)
                }
                stack.push(line)
            }
            if (line.modifier<PositionLineModifier.EndAvgSpeed>() != null) {
                if (stack.isNotEmpty()) {
                    updateBy(stack, matching, line, subNumbers)
                }
            }
        }
        return SubsMatch(subNumbers, matching)
    }

    private fun updateBy(
        stack: Stack<PositionLine>,
        matching: MutableMap<PositionLine, PositionLine>,
        line: PositionLine,
        subNumbers: MutableMap<PositionLine, Int>
    ) {
        val set = stack.pop()
        matching[line] = set
        subNumbers.compute(line) { _, _ -> subNumbers[set] }
    }
}

data class SubsMatch(
    val subNumbers: Map<PositionLine, Int>,
    val endSubMatch: Map<PositionLine, PositionLine>
) {
    companion object {
        val EMPTY = SubsMatch(emptyMap(), emptyMap())
    }
}