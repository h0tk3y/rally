package com.h0tk3y.rally

data class RallyTimesResult(
    val timeVectorsAtRoadmapLine: Map<LineNumber, TimeHrVector>,
    val goAtAvgSpeed: Map<LineNumber, SpeedKmh?>
)

class RallyTimesCalculator {
    fun rallyTimes(roadmap: List<PositionLine>): RallyTimesResult {
        val timeAtPositions = mutableMapOf<LineNumber, TimeHrVector>()
        val pureSpeedAtSubs = mutableMapOf<LineNumber, SpeedKmh>()

        data class CallResult(
            val endInclusive: Int,
            val totalTime: TimeHr
        )

        fun recurse(startAt: Int, setavg: PositionLine.SetAvgSpeed?): CallResult {
            val subs = mutableMapOf<Int, CallResult>()

            var endIndexInclusive = -1
            // first, make all recursive calls for the sub-intervals; those will calculate their local times
            // also, find the matching endavg for the given setavg
            run {
                var i = startAt
                while (i <= roadmap.lastIndex /* but will likely break earlier */) {
                    val line = roadmap[i]

                    if ((i != startAt || setavg == null) && line is PositionLine.SetAvgSpeed) {
                        val sub = recurse(i, line)
                        subs[i] = sub
                        i = sub.endInclusive + 1
                    } else if (line is PositionLine.EndAvgSpeed) {
                        endIndexInclusive = i
                        checkNotNull(setavg) { "found endavg ${line.avgspeed} at ${line.lineNumber}, but there is no setavg before" }
                        check(line.avgspeed == line.avgspeed) { "unmatched endavg, expected ${setavg.avgspeed} " }
                        break
                    } else {
                        checkNotNull(setavg) { "found a mark ${line.atKm} at ${line.lineNumber} with no setavg before" }
                        ++i
                    }
                }
            }

            if (setavg == null) {
                return subs.values.single()
            }
            if (endIndexInclusive == -1 && startAt == 0) {
                endIndexInclusive = roadmap.lastIndex
            }

            val allDst = roadmap[endIndexInclusive].atKm - roadmap[startAt].atKm
            val pureDst = run {
                val exemptDistance = subs.entries
                    .map { (from, sub) -> roadmap[sub.endInclusive].atKm - roadmap[from].atKm }
                    .fold(DistanceKm.zero, DistanceKm::plus)
                allDst - exemptDistance
            }

            val allTime = TimeHr.byMoving(allDst, setavg.avgspeed)
            val pureTime = run {
                allTime - subs.values.fold(TimeHr.zero) { acc, it -> acc + it.totalTime }
            }

            val pureSpeed = SpeedKmh.averageAt(pureDst, pureTime)
            pureSpeedAtSubs[roadmap[startAt].lineNumber] = pureSpeed

            // iterate over the positions not handled by the nested recursive calls, calculate the time for them: 
            run {
                var localTime = TimeHr.zero
                var i = startAt

                while (i <= endIndexInclusive) {
                    val line = roadmap[i]
                    val lineNumber = line.lineNumber

                    timeAtPositions[lineNumber]
                    val prevAtKm = run {
                        val prevIndex = when (i) {
                            startAt -> startAt // we want to have "moved 0km" at the start of each sub
                            else -> i - 1
                        }
                        roadmap[prevIndex].atKm
                    }
                    val intervalDistance = line.atKm - prevAtKm
                    val intervalTime = TimeHr.byMoving(intervalDistance, pureSpeed)
                    localTime += intervalTime

                    when (val sub = subs[i]) {
                        null -> {
                            timeAtPositions.compute(lineNumber) { _, old ->
                                check(old == null)
                                TimeHrVector.of(localTime)
                            }
                            ++i
                        }

                        else -> {
                            val subEnd = sub.endInclusive
                            for (j in i..subEnd) {
                                timeAtPositions.compute(roadmap[j].lineNumber) { _, old ->
                                    checkNotNull(old)
                                    old.rebased(localTime)
                                }
                            }
                            val endLineNumber = roadmap[subEnd].lineNumber
                            localTime = timeAtPositions.getValue(endLineNumber).outer
                            pureSpeedAtSubs[endLineNumber] = pureSpeed
                            i = subEnd + 1
                        }
                    }
                }
            }
            return CallResult(endIndexInclusive, allTime)
        }

        recurse(0, null)

        return RallyTimesResult(timeAtPositions, pureSpeedAtSubs)
    }
}