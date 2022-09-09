package com.h0tk3y.rally

data class RallyTimesResult(
    val timeVectorsAtRoadmapLine: Map<LineNumber, TimeHrVector>,
    val goAtAvgSpeed: Map<LineNumber, SpeedKmh?>
)

class RallyTimesCalculator {
    private data class RecursiveCallResult(
        val endInclusive: Int,
        val totalTime: TimeHr,
    )

    companion object {
        private fun getExemptDistance(
            roadmap: List<PositionLine>,
            subs: Map<Int, RecursiveCallResult>,
            from: Int
        ): DistanceKm =
            subs.entries
                .filter { it.key > from }
                .map { (from, sub) -> roadmap[sub.endInclusive].atKm - roadmap[from].atKm }
                .fold(DistanceKm.zero, DistanceKm::plus)

        private fun getExemptTime(subs: Map<Int, RecursiveCallResult>, from: Int): TimeHr =
            subs.entries
                .filter { it.key > from }
                .map { (_, sub) -> sub.totalTime }
                .fold(TimeHr.zero, TimeHr::plus)
    }

    fun rallyTimes(roadmap: List<PositionLine>): RallyTimesResult {
        val timeByLine = mutableMapOf<LineNumber, TimeHrVector>()
        val pureSpeedAtSubs = mutableMapOf<LineNumber, SpeedKmh>()

        fun nextIndex(sub: RecursiveCallResult) =
            if (roadmap[sub.endInclusive].isThenAvg) sub.endInclusive else sub.endInclusive + 1

        fun recurse(roadmap: List<PositionLine>, startAtIndex: Int, from: PositionLine?): RecursiveCallResult {
            val avg = from?.modifier<PositionLineModifier.SetAvg>()?.setavg

            val subs = mutableMapOf<Int, RecursiveCallResult>()

            var endInclusive = -1
            // first, make all recursive calls for the sub-intervals; those will calculate their local times
            // also, find the matching endavg for the given setavg
            run {
                var i = startAtIndex
                while (i <= roadmap.lastIndex /* but will likely break earlier */) {
                    val line = roadmap[i]

                    when {
                        i != startAtIndex && line.isEndAvg -> {
                            endInclusive = i
                            checkNotNull(avg) { "found sub end at ${line.lineNumber}, but there is no setavg before" }
                            val endavg = line.modifier<PositionLineModifier.EndAvg>()?.endavg
                            if (endavg != null) {
                                check(avg == endavg) { "unmatched endavg, expected ${avg} " }
                            }
                            break
                        }

                        (i != startAtIndex || from == null) && line.isSetAvg -> {
                            do {
                                val sub = recurse(roadmap, i, roadmap[i])
                                subs[i] = sub
                                i = nextIndex(sub)
                            } while (roadmap[sub.endInclusive].isThenAvg)
                        }

                        else -> {
                            checkNotNull(avg) { "found a mark ${line.atKm} at ${line.lineNumber} with no setavg before" }
                            ++i
                        }
                    }
                }
            }

            if (endInclusive == -1) {
                endInclusive = roadmap.lastIndex
            }

            // iterate over the positions not handled by the nested recursive calls, calculate the time for them: 
            var pureSpeed = SpeedKmh(0.0)
            var localTime = TimeHr.zero
            var isCoveredBySubs = false

            fun updatePureSpeed(roadmap: List<PositionLine>, endInclusive: Int, startAtIndex: Int, atIndex: Int) {
                val start = roadmap[startAtIndex]
                val end = roadmap[endInclusive]
                val allDst = end.atKm - start.atKm
                val at = roadmap[atIndex]
                val passed = at.atKm - start.atKm
                val pureDst = allDst - passed - getExemptDistance(roadmap, subs, startAtIndex)
                if (pureDst.valueKm < 0.01) {
                    isCoveredBySubs = true
                    return
                }

                val allTime = TimeHr.byMoving(allDst, checkNotNull(avg))
                val exemptTime = getExemptTime(subs, startAtIndex)
                val pureTime = (allTime - localTime - exemptTime)
                if (pureTime.timeHours < 0.0001) {
                    println(
                        "\n(!!!) Impossible to get in time; subs from ${start.lineNumber} to ${end.lineNumber} take ${exemptTime.toMinSec()}, while available time is ${allTime.toMinSec()}\n"
                    )
                }

                pureSpeed = SpeedKmh.averageAt(pureDst, pureTime)
                pureSpeedAtSubs[at.lineNumber] = pureSpeed
            }

            if (avg != null) {
                updatePureSpeed(roadmap, endInclusive, startAtIndex, startAtIndex)
            } else {
                check(
                    subs.map { it.key..it.value.endInclusive }.flatten().toSet()
                        .containsAll((startAtIndex..endInclusive).toList())
                )
                isCoveredBySubs = true
            }
            var i = startAtIndex

            while (i <= endInclusive) {
                val line = roadmap[i]
                val lineNumber = line.lineNumber

                val prevAtKm = run {
                    val prevIndex = when (i) {
                        startAtIndex -> startAtIndex // we want to have "moved 0km" at the start of each sub
                        else -> i - 1
                    }
                    roadmap[prevIndex].atKm
                }
                val intervalDistance = line.atKm - prevAtKm
                val intervalTime = if (isCoveredBySubs) TimeHr.zero else TimeHr.byMoving(intervalDistance, pureSpeed)
                localTime += intervalTime

                line.modifier<PositionLineModifier.Here>()?.let { here ->
                    localTime = here.atTime
                    updatePureSpeed(roadmap, endInclusive, startAtIndex, i)
                }

                when (val sub = subs[i]) {
                    null -> {
                        timeByLine.compute(lineNumber) { key, old ->
                            check(old == null || i == startAtIndex)
                            old ?: TimeHrVector.of(localTime)
                        }
                        ++i
                    }

                    else -> {
                        val subEnd = sub.endInclusive
                        for (j in i..subEnd) {
                            if (j != i || !roadmap[i].isThenAvg) {
                                timeByLine.compute(roadmap[j].lineNumber) { _, old ->
                                    checkNotNull(old)
                                    if (isCoveredBySubs && subs.size == 1) 
                                        old 
                                    else
                                        old.rebased(localTime)
                                }
                            }
                        }
                        val endLineNumber = roadmap[subEnd].lineNumber
                        localTime = timeByLine.getValue(endLineNumber).outer
                        if (!isCoveredBySubs && 
                            subEnd != endInclusive && 
                            !roadmap[subEnd].isThenAvg &&
                            (roadmap[subEnd + 1].atKm - roadmap[subEnd].atKm).valueKm >= 0.1
                        ) {
                            pureSpeedAtSubs[endLineNumber] = pureSpeed
                        }
                        i = nextIndex(sub)
                    }
                }
            }
            return RecursiveCallResult(
                endInclusive = endInclusive,
                timeByLine.getValue(roadmap[endInclusive].lineNumber).outer - timeByLine.getValue(roadmap[startAtIndex].lineNumber).outer
            )
        }

        recurse(roadmap, 0, null)

        return RallyTimesResult(timeByLine, pureSpeedAtSubs)
    }
}