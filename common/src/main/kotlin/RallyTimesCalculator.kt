package com.h0tk3y.rally

sealed interface RallyTimesResult

data class RallyTimesResultSuccess(
    val timeVectorsAtRoadmapLine: Map<LineNumber, TimeHrVector>,
    val goAtAvgSpeed: Map<LineNumber, SpeedKmh?>,
    val warnings: List<CalculationWarning>
) : RallyTimesResult

data class RallyTimesResultFailure(
    val failures: List<CalculationFailure>
) : RallyTimesResult

data class CalculationWarning(
    val line: RoadmapInputLine,
    val reason: WarningReason
)

sealed interface WarningReason {
    data class ImpossibleToGetInTime(val start: RoadmapInputLine, val takes: TimeHr, val available: TimeHr) :
        WarningReason
}

data class CalculationFailure(
    val line: RoadmapInputLine,
    val reason: FailureReason
)

sealed interface FailureReason {
    object AverageSpeedUnknown : FailureReason
    object UnexpectedAverageEnd : FailureReason
    object NonMatchingAverageEnd : FailureReason
    object OuterIntervalNotCovered : FailureReason
    data class DistanceIsNotIncreasing(val shouldBeAtLeast: DistanceKm) : FailureReason
}

interface RallyTimes {
    fun rallyTimes(roadmap: List<PositionLine>): RallyTimesResult
}

class RallyTimesCalculator : RallyTimes {
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

    override fun rallyTimes(roadmap: List<PositionLine>): RallyTimesResult {
        val preResult = validateRoadmap(roadmap)
        if (preResult is RallyTimesResultFailure) {
            return preResult
        }
        
        val timeByLine = mutableMapOf<LineNumber, TimeHrVector>()
        val pureSpeedAtSubs = mutableMapOf<LineNumber, SpeedKmh>()

        val failures: MutableList<CalculationFailure> = mutableListOf()
        val warnings: MutableList<CalculationWarning> = mutableListOf()

        fun nextIndex(sub: RecursiveCallResult) =
            if (roadmap[sub.endInclusive].isThenAvg) sub.endInclusive else sub.endInclusive + 1

        fun recurse(roadmap: List<PositionLine>, startAtIndex: Int, from: PositionLine?): RecursiveCallResult {
            val avg = from?.modifier<PositionLineModifier.SetAvg>()?.setavg

            val subs = mutableMapOf<Int, RecursiveCallResult>()

            if (roadmap.isEmpty()) {
                return RecursiveCallResult(startAtIndex, TimeHr.zero)
            }

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
                            if (avg == null) {
                                failures.add(CalculationFailure(line, FailureReason.UnexpectedAverageEnd))
                            }
                            val endavg = line.modifier<PositionLineModifier.EndAvg>()?.endavg
                            if (endavg != null) {
                                failures.add(CalculationFailure(line, FailureReason.NonMatchingAverageEnd))
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
                if (pureDst.valueKm < 0.001) {
                    isCoveredBySubs = true
                    return
                }

                val allTime = TimeHr.byMoving(allDst, avg ?: SpeedKmh(999.0))
                val exemptTime = getExemptTime(subs, startAtIndex)
                val pureTime = (allTime - localTime - exemptTime)
                if (pureTime.timeHours < 0.0001) {
                    warnings.add(
                        CalculationWarning(end, WarningReason.ImpossibleToGetInTime(start, exemptTime, allTime))
                    )

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
                val positionsInSubs = subs.map { it.key..it.value.endInclusive }.flatten().toSet()
                val positionsNotInSubs = (startAtIndex..endInclusive).filter { it !in positionsInSubs }
                if (positionsNotInSubs.isEmpty()) {
                    isCoveredBySubs = true
                } else {
                    failures.addAll(positionsNotInSubs.map {
                        CalculationFailure(roadmap[it], FailureReason.AverageSpeedUnknown)
                    })
                }
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
                        timeByLine.compute(lineNumber) { _, old ->
                            check(old == null || i == startAtIndex)
                            old ?: TimeHrVector.of(localTime)
                        }
                        ++i
                    }

                    else -> {
                        val subEnd = sub.endInclusive
                        val subIsThenAvg = roadmap[i].isThenAvg
                        for (j in i..subEnd) {
                            if (j != i || !subIsThenAvg) {
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
                timeByLine.getValue(roadmap[endInclusive].lineNumber).outer/* - timeByLine.getValue(roadmap[startAtIndex].lineNumber).outer*/
            )
        }

        recurse(roadmap, 0, null)

        if (failures.isNotEmpty()) {
            return RallyTimesResultFailure(failures)
        }

        return RallyTimesResultSuccess(timeByLine, pureSpeedAtSubs, warnings)
    }

}

fun validateRoadmap(roadmap: Iterable<RoadmapInputLine>): RallyTimesResultFailure? {
    val failures = mutableListOf<CalculationFailure>()
    var max = Double.NEGATIVE_INFINITY
    roadmap.filterIsInstance<PositionLine>().forEach {
        if (it.atKm.valueKm < max - 0.0001) {
            failures.add(CalculationFailure(it, FailureReason.DistanceIsNotIncreasing(DistanceKm(max))))
        }
        max = maxOf(max, it.atKm.valueKm)
    }
    
    var depth = 0
    roadmap.filterIsInstance<PositionLine>().forEach {
        if (it.isSetAvg) {
            depth++
        } else if (it.isEndAvg) {
            depth--
            if (depth < 0) {
                failures.add(CalculationFailure(it, FailureReason.UnexpectedAverageEnd))
            }
        } else if (depth <= 0) {
            failures.add(CalculationFailure(it, FailureReason.AverageSpeedUnknown))
        }
    }
    
    return if (failures.isNotEmpty())
        RallyTimesResultFailure(failures)
    else null
}
