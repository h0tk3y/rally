package com.h0tk3y.rally

import com.h0tk3y.rally.PositionLineModifier.SetAvg

class RallyTimesIntervalsCalculator : RallyTimes {
    override fun rallyTimes(roadmap: List<PositionLine>): RallyTimesResult {
        val preValidation = validateRoadmap(roadmap)
        if (preValidation is RallyTimesResultFailure) {
            return preValidation
        }

        val rootInterval = IntervalBuilder().buildInterval(roadmap, 0, roadmap.lastIndex)
        if (rootInterval is IntervalBuilder.BuildIntervalResult.Failure) {
            return RallyTimesResultFailure(listOf(rootInterval.failure))
        }
        check(rootInterval is IntervalBuilder.BuildIntervalResult.Result)

        val result = IntervalTimesEvaluator().evaluate(rootInterval.interval, rootInterval.interval.end)
        check(result is RallyTimesResultSuccess)

        return result.copy(astroTimeAtRoadmapLine = AstroTimeCalculator.calculateAstroTimes(roadmap = roadmap, timeHrMap = result.timeVectorsAtRoadmapLine))
    }
}



internal data class PureFragment(
    val start: PositionLine,
    val end: PositionLine,
) {
    val distance: DistanceKm = end.atKm - start.atKm
}

internal data class Interval(
    val start: PositionLine,
    val end: PositionLine,
    val pureFragments: List<PureFragment>,
    val subIntervals: List<Interval>,
    val targetAverageSpeedKmh: SpeedKmh,
    val isSynthRoot: Boolean = false
) {

    val pureDistance: DistanceKm = DistanceKm(pureFragments.sumOf { it.distance.valueKm })
    val targetTime: TimeHr = TimeHr.byMovingOrZero(start.atKm, end.atKm, targetAverageSpeedKmh)

    val exemptTime: TimeHr = TimeHr(subIntervals.sumOf { it.targetTime.timeHours })
    val pureTime: TimeHr = maxOf(if (targetTime.timeHours.isInfinite()) targetTime else targetTime - exemptTime, TimeHr.zero)

    val pureSpeed: SpeedKmh = SpeedKmh.averageAt(pureDistance, pureTime)
}

private fun TimeHr.Companion.byMovingOrZero(startKm: DistanceKm, endKm: DistanceKm, speedKmh: SpeedKmh) = when {
    startKm == endKm -> TimeHr(0.0)
    else -> byMoving(endKm - startKm, speedKmh)
}

internal class IntervalTimesEvaluator {

    fun evaluate(rootInterval: Interval, last: PositionLine): RallyTimesResult {
        val timeHrMap = mutableMapOf<PositionLine, TimeHrVector>()
        val warnings = mutableListOf<CalculationWarning>()
        val goAtMap = mutableMapOf<PositionLine, SpeedKmh>()

        fun putInnermostTime(positionLine: PositionLine, time: TimeHr) {
            timeHrMap.compute(positionLine) { key, old ->
                check(old == null) { "Expected this time to be the innermost" }
                TimeHrVector.of(time)
            }
        }

        fun rebase(positionLine: PositionLine, timeHr: TimeHr) {
            timeHrMap.compute(positionLine) { _, old ->
                check(old != null) { "Expected existing time for this position, as it is now rebased" }
                old.rebased(timeHr)
            }
        }

        fun traverseAndRebase(interval: Interval, addTime: TimeHr) {
            interval.pureFragments.forEach { pureFragment ->
                rebase(pureFragment.end, addTime)
            }
            interval.subIntervals.forEach { subInterval ->
                traverseAndRebase(subInterval, addTime)
            }
        }

        fun recurse(
            interval: Interval,
            startLocalTime: TimeHr = TimeHr.zero
        ): TimeHr {
            val sortedEvents = buildList<Event> {
                addAll(interval.pureFragments.map(Event::PureFragmentStart))
                addAll(interval.subIntervals.map(Event::SubIntervalStart))
                sortBy { it.startDistance.valueKm }
            }

            if (interval.exemptTime > interval.targetTime && !interval.isSynthRoot) {
                warnings.add(
                    CalculationWarning(
                        interval.end,
                        WarningReason.ImpossibleToGetInTime(interval.start, interval.exemptTime, interval.targetTime)
                    )
                )
            } else { // it is negative when we can't get in time, so don't pollute the outputs
                if (interval.subIntervals.isNotEmpty() && interval.pureFragments.isNotEmpty()) {
                    goAtMap[interval.start] = interval.pureSpeed
                }
            }

            var localTime = startLocalTime
            var lastSubTime = TimeHr.zero
            var setAvgTime = TimeHr.zero

            sortedEvents.forEach { event ->
                when (event) {
                    is Event.PureFragmentStart -> {
                        val fragment = event.pureFragment
                        localTime += maxOf(TimeHr.zero, TimeHr.byMoving(fragment.distance, interval.pureSpeed))
                        putInnermostTime(fragment.end, localTime)
                    }

                    is Event.SubIntervalStart -> {
                        val subInterval = event.subInterval
                        val subStartTime = if (subInterval.start.isEndAvg) lastSubTime else TimeHr.zero
                        val subTime = recurse(subInterval, subStartTime)

                        if (!subInterval.start.isThenAvg) {
                            setAvgTime = localTime
                        }

                        if (setAvgTime != TimeHr.zero) {
                            traverseAndRebase(subInterval, setAvgTime)
                        }
                        if (!subInterval.end.isThenAvg && interval.pureFragments.isNotEmpty() && subInterval.end != last) {
                            goAtMap[subInterval.end] = interval.pureSpeed
                        }

                        localTime += if (subTime.timeHours.isInfinite()) subTime else subTime - subStartTime
                        lastSubTime = subTime
                    }
                }
            }

            return localTime
        }

        recurse(rootInterval, TimeHr.zero)
        return RallyTimesResultSuccess(
            timeHrMap.mapKeys { it.key.lineNumber }, emptyMap(), goAtMap.mapKeys { it.key.lineNumber }, warnings
        )
    }

    private sealed interface Event {
        val startDistance: DistanceKm

        data class PureFragmentStart(val pureFragment: PureFragment) : Event {
            override val startDistance: DistanceKm
                get() = pureFragment.start.atKm
        }

        data class SubIntervalStart(val subInterval: Interval) : Event {
            override val startDistance: DistanceKm
                get() = subInterval.start.atKm
        }
    }
}

internal class IntervalBuilder {
    sealed interface BuildIntervalResult {
        data class Result(val interval: Interval) : BuildIntervalResult
        data class Failure(val failure: CalculationFailure) : BuildIntervalResult
    }

    fun buildInterval(roadmap: List<PositionLine>, startAt: Int, endAt: Int): BuildIntervalResult {
        data class IntervalResult(val interval: Interval, val endIndex: Int)

        if (roadmap.isEmpty()) {
            return BuildIntervalResult.Failure(
                CalculationFailure(
                    PositionLine(DistanceKm(0.0), LineNumber(1, 0), emptyList()),
                    FailureReason.AverageSpeedUnknown
                )
            )
        }

        fun recurse(startAt: Int, avgSpeed: SpeedKmh): IntervalResult {
            val start = roadmap[startAt]

            val pureFragments = mutableListOf<PureFragment>()
            val subs = mutableListOf<Interval>()

            val endIndex = run {
                var index = startAt
                var prev = start
                var afterSub = start.isThenAvg

                while (index <= endAt) {
                    val currentPosition = roadmap[index]
                    if (currentPosition.atKm != prev.atKm) {
                        pureFragments += PureFragment(prev, currentPosition)
                    }

                    if (currentPosition.isEndAvg && !afterSub) { // is endavg or thenavg
                        break // found the sub-interval end
                    } else if (index != startAt && currentPosition.isSetAvg) { // only setavg goes here
                        val sub = recurse(index, currentPosition.modifier<SetAvg>()!!.setavg)
                        subs += sub.interval

                        prev = roadmap[sub.endIndex.coerceAtMost(endAt)]
                        afterSub = true
                        index = sub.endIndex
                    } else {
                        afterSub = false
                        prev = currentPosition
                        index++
                    }
                }

                index
            }
            return IntervalResult(
                Interval(start, roadmap[endIndex.coerceAtMost(endAt)], pureFragments, subs, avgSpeed), endIndex
            )
        }

        var outermost: Interval? = null
        var current = startAt
        var hasSynthRoot = false
        do {
            val setAvg = roadmap[current].modifier<SetAvg>()
                ?: return BuildIntervalResult.Failure(
                    CalculationFailure(
                        roadmap[current],
                        FailureReason.OuterIntervalNotCovered
                    )
                )

            val call = recurse(current, setAvg.setavg)
            if (outermost != null) {
                outermost =
                    outermost.copy(end = call.interval.end, subIntervals = outermost.subIntervals + call.interval)
                hasSynthRoot = true
            }
            if (call.endIndex >= endAt) {
                return BuildIntervalResult.Result(outermost?.maybeAsSynthRoot(hasSynthRoot) ?: call.interval)
            } else {
                outermost = outermost
                    ?: Interval(
                        call.interval.start,
                        call.interval.end,
                        emptyList(),
                        listOf(call.interval),
                        SpeedKmh(60.0)
                    )
            }
            current = call.endIndex
        } while (current < endAt)

        check(outermost != null)
        outermost.subIntervals.zipWithNext().forEach { (a, b) ->
            if (b.start.atKm != a.end.atKm) {
                return BuildIntervalResult.Failure(CalculationFailure(b.end, FailureReason.OuterIntervalNotCovered))
            }
        }
        return BuildIntervalResult.Result(outermost.maybeAsSynthRoot(hasSynthRoot))
    }
}

private fun Interval.maybeAsSynthRoot(hasSynthRoot: Boolean) = if (hasSynthRoot) copy(isSynthRoot = true) else this

fun main() {
    val input = """
    0.0 setavg 60.0
    0.5 setavg 50.0
    0.6 setavg 0.0
    10.0 endavg 
    """.trimIndent()

    val roadmap =
        InputRoadmapParser(DefaultModifierValidator()).parseRoadmap(input.reader()).filterIsInstance<PositionLine>()

    println(roadmap)

    val interval = IntervalBuilder().buildInterval(roadmap, 0, roadmap.lastIndex)
    println(interval)
    check(interval is IntervalBuilder.BuildIntervalResult.Result)

    val result = IntervalTimesEvaluator().evaluate(interval.interval, interval.interval.end)
    check(result is RallyTimesResultSuccess)

    val averages = calculateAverages(roadmap, result)
    println(ResultsFormatter().formatResults(roadmap, result, averages, 1.0))
}