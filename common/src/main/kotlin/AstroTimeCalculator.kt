package com.h0tk3y.rally

internal object AstroTimeCalculator {
    fun calculateAstroTimes(
        roadmap: List<PositionLine>,
        timeHrMap: Map<LineNumber, TimeHrVector>
    ): Map<LineNumber, TimeOfDay> {
        val astroTimeLine = roadmap.singleOrNull { it.modifier<PositionLineModifier.AstroTime>() != null }
        if (astroTimeLine == null) {
            return emptyMap()
        }
        val startAstroTime = astroTimeLine.modifier<PositionLineModifier.AstroTime>()!!.timeOfDay
        val startOuterTime = timeHrMap[astroTimeLine.lineNumber]?.outer ?: TimeHr.zero

        return buildMap {
            for (line in roadmap) {
                if (timeHrMap[line.lineNumber]?.outer?.let { it > startOuterTime } == true) {
                    val timeSinceAstroStart = timeHrMap.getValue(line.lineNumber).outer - startOuterTime
                    if (timeSinceAstroStart.timeHours.isFinite() && timeSinceAstroStart > TimeHr.zero) {
                        val atime = startAstroTime + timeSinceAstroStart.toMinSec()
                        put(line.lineNumber, atime)
                    }
                }
            }
        }
    }

}