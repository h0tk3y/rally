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
            val indexAfterSub: Int,
            val totalTime: TimeHr
        )

        fun recurse(startAtIndex: Int): CallResult {
            val from = roadmap[startAtIndex]
            val avg = from.modifier<PositionLineModifier.SetAvgSpeed>()?.setavg
            
            val subs = mutableMapOf<Int, CallResult>()

            var endIndexInclusive = -1
            // first, make all recursive calls for the sub-intervals; those will calculate their local times
            // also, find the matching endavg for the given setavg
            run {
                var i = startAtIndex
                while (i <= roadmap.lastIndex /* but will likely break earlier */) {
                    val line = roadmap[i]

                    if (i != startAtIndex && line.isSetAvg) {
                        val sub = recurse(i)
                        subs[i] = sub
                        i = sub.indexAfterSub
                    } else if (line.isEndAvg) {
                        endIndexInclusive = i
                        checkNotNull(avg) { "found sub end at ${line.lineNumber}, but there is no setavg before" }
                        val endavg = line.modifier<PositionLineModifier.EndAvg>()?.endavg
                        if (endavg != null) {
                            check(avg == endavg) { "unmatched endavg, expected ${avg} " }
                        }
                        break
                    } else {
                        checkNotNull(avg) { "found a mark ${line.atKm} at ${line.lineNumber} with no setavg before" }
                        ++i
                    }
                }
            }

            if (endIndexInclusive == -1 && startAtIndex == 0) {
                endIndexInclusive = roadmap.lastIndex
            }

            val allDst = roadmap[endIndexInclusive].atKm - roadmap[startAtIndex].atKm
            val pureDst = run {
                val exemptDistance = subs.entries
                    .map { (from, sub) -> roadmap[sub.endInclusive].atKm - roadmap[from].atKm }
                    .fold(DistanceKm.zero, DistanceKm::plus)
                allDst - exemptDistance
            }

            val allTime = TimeHr.byMoving(allDst, checkNotNull(avg))
            val pureTime = run {
                allTime - subs.values.fold(TimeHr.zero) { acc, it -> acc + it.totalTime }
            }

            val pureSpeed = SpeedKmh.averageAt(pureDst, pureTime)
            pureSpeedAtSubs[roadmap[startAtIndex].lineNumber] = pureSpeed

            // iterate over the positions not handled by the nested recursive calls, calculate the time for them: 
            run {
                var localTime = TimeHr.zero
                var i = startAtIndex

                while (i <= endIndexInclusive) {
                    val line = roadmap[i]
                    val lineNumber = line.lineNumber

                    timeAtPositions[lineNumber]
                    val prevAtKm = run {
                        val prevIndex = when (i) {
                            startAtIndex -> startAtIndex // we want to have "moved 0km" at the start of each sub
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
                            i = sub.indexAfterSub
                        }
                    }
                }
            }
            return CallResult(
                endInclusive = endIndexInclusive,
                indexAfterSub =  if (roadmap[endIndexInclusive].isThenAvg) endIndexInclusive else endIndexInclusive + 1,
                allTime
            )
        }

        recurse(0)

        return RallyTimesResult(timeAtPositions, pureSpeedAtSubs)
    }
}