package com.h0tk3y.rally

fun calculateAverages(
    input: Collection<RoadmapInputLine>,
    result: RallyTimesResultSuccess
): Map<LineNumber, SpeedKmh> = buildMap { 
    var startDst = DistanceKm.zero
    var startTime = TimeHr.zero
    var calculating = false
    for (i in input.filterIsInstance<PositionLine>()) {
        if (calculating) {
            val dstFromStart = i.atKm - startDst
            val timeFromStart = result.timeVectorsAtRoadmapLine.getValue(i.lineNumber).outer - startTime
            val speed = SpeedKmh.averageAt(dstFromStart, timeFromStart)
            put(i.lineNumber, speed)
        }
        if (i.modifier<PositionLineModifier.CalculateAverage>() != null) {
            check(!calculating) { "unexpected calc at ${i.lineNumber}, there was one already" }
            calculating = true
            startDst = i.atKm
            startTime = result.timeVectorsAtRoadmapLine.getValue(i.lineNumber).outer
        }
        if (i.modifier<PositionLineModifier.EndCalculateAverage>() != null) {
            check(calculating) { "unexpected endcalc at ${i.lineNumber}, there was no calc" }
            calculating = false
        }
    }
} 