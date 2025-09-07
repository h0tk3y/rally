package com.h0tk3y.rally

import com.h0tk3y.rally.PositionLineModifier.SetAvg

internal class NoopTimeDistanceLocalizer(override val base: PositionLine) : TimeDistanceLocalizer {
    override fun getExpectedTimeForDistance(distanceKm: DistanceKm) = null
}

internal class TimeDistanceLocalizerImpl(
    override val base: PositionLine,
    private val positions: List<PositionLine>,
    private val fragmentSpeed: Map<PureFragment, SpeedKmh>,
    private val pureFragments: List<PureFragment>
) : TimeDistanceLocalizer {
    override fun getExpectedTimeForDistance(distanceKm: DistanceKm): TimeHr {
        val baseDistance = base.atKm
        val (from, to) = listOf(baseDistance, distanceKm).sorted()

        val wholeFragments = pureFragments.filter {
            it.start.atKm >= from && it.end.atKm <= to
        }

        val wholeFragmentsTime = wholeFragments.fold(TimeHr.zero) { acc, it ->
            acc + TimeHr.byMovingOrZero(it.start.atKm, it.end.atKm, fragmentSpeed.getValue(it))
        }

        fun PureFragment.speedBeforeIfUnknown(): SpeedKmh = fragmentSpeed.getValue(this)
        fun PureFragment.speedAfterIfUnknown(): SpeedKmh = end.modifier<SetAvg>()?.setavg ?: fragmentSpeed.getValue(this)

        val timeInOrBeforeFirst = run {
            val startInPartialFragment = pureFragments.firstOrNull { from > it.start.atKm && from <= it.end.atKm }
            if (startInPartialFragment != null) {
                TimeHr.byMovingOrZero(from, minOf(startInPartialFragment.end.atKm, to), fragmentSpeed.getValue(startInPartialFragment))
            } else {
                val startBeforeFirstFragment = pureFragments.firstOrNull()?.takeIf { it.start.atKm in from..to }
                startBeforeFirstFragment?.let { TimeHr.byMovingOrZero(from, it.start.atKm, it.speedBeforeIfUnknown()) } ?: TimeHr.zero
            }
        }

        val timeInOrAfterLast = run {
            val finishInPartialFragment = pureFragments.lastOrNull { to >= it.start.atKm && to < it.end.atKm }
            if (finishInPartialFragment != null) {
                if (from > finishInPartialFragment.start.atKm && from <= finishInPartialFragment.end.atKm) {
                    TimeHr.zero // already taken into account as the starting partial fragment
                } else {
                    TimeHr.byMovingOrZero(finishInPartialFragment.start.atKm, to, fragmentSpeed.getValue(finishInPartialFragment))
                }
            } else {
                val finishAfterLastFragment = pureFragments.lastOrNull()?.takeIf { it.end.atKm in from..to }
                finishAfterLastFragment?.let {
                    val speedAfterLast =
                        positions.lastOrNull(PositionLine::isSetAvg)?.modifier<SetAvg>()?.setavg ?: finishAfterLastFragment.speedAfterIfUnknown()
                    TimeHr.byMovingOrZero(it.end.atKm, to, speedAfterLast)
                } ?: TimeHr.zero
            }
        }

        return (timeInOrBeforeFirst + wholeFragmentsTime + timeInOrAfterLast)
            .let { if (to == base.atKm) it.copy(timeHours = -it.timeHours) else it }
    }

    companion object {
        fun createWithNestedIntervals(rootInterval: Interval, positions: List<PositionLine>): TimeDistanceLocalizer {
            val (pureSpeedMap, pureFragments) = buildPureFragmentsAndSpeeds(rootInterval)
            if (pureFragments.isEmpty()) {
                val syntheticFragment = PureFragment(rootInterval.start, rootInterval.end)
                return TimeDistanceLocalizerImpl(
                    rootInterval.start,
                    positions,
                    mapOf(syntheticFragment to (rootInterval.start.modifier<SetAvg>()?.setavg ?: return NoopTimeDistanceLocalizer(rootInterval.start))),
                    listOf(syntheticFragment)
                )
            }

            val basePositionLine = positions.find { it.modifier<PositionLineModifier.AstroTime>() != null }
                ?: rootInterval.start

            return TimeDistanceLocalizerImpl(
                basePositionLine,
                positions,
                pureSpeedMap,
                pureFragments
            )
        }

        fun createForRootIntervalOnly(rootInterval: Interval, positions: List<PositionLine>): TimeDistanceLocalizer {
            val (_, pureFragments) = buildPureFragmentsAndSpeeds(rootInterval)
            if (pureFragments.isEmpty()) {
                val syntheticFragment = PureFragment(rootInterval.start, rootInterval.end)
                val speedKmh = rootInterval.start.modifier<SetAvg>()?.setavg ?: return NoopTimeDistanceLocalizer(rootInterval.start)
                return TimeDistanceLocalizerImpl(
                    rootInterval.start,
                    positions.take(1),
                    mapOf(syntheticFragment to speedKmh),
                    listOf(syntheticFragment)
                )
            }

            val basePositionLine = positions.find { it.modifier<PositionLineModifier.AstroTime>() != null }
                ?: rootInterval.start

            val singleFragment = PureFragment(rootInterval.start, rootInterval.end)
            val rootFragmentSpeed = mapOf(singleFragment to rootInterval.targetAverageSpeedKmh)

            return TimeDistanceLocalizerImpl(
                basePositionLine,
                positions.take(1),
                rootFragmentSpeed,
                listOf(singleFragment)
            )
        }

        private fun buildPureFragmentsAndSpeeds(rootInterval: Interval): Pair<MutableMap<PureFragment, SpeedKmh>, List<PureFragment>> {
            val pureSpeedMap = mutableMapOf<PureFragment, SpeedKmh>()

            fun recurseAt(interval: Interval) {
                val speed = interval.pureSpeed
                interval.pureFragments.forEach {
                    pureSpeedMap[it] = speed
                }
                interval.subIntervals.forEach(::recurseAt)
            }

            recurseAt(rootInterval)

            val pureFragments = pureSpeedMap.keys.sortedBy { it.start.atKm }
            return Pair(pureSpeedMap, pureFragments)
        }
    }
}