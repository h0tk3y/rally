package com.h0tk3y.rally.model

import com.h0tk3y.rally.TimeDistanceLocalizer
import com.h0tk3y.rally.TimeMinSec
import com.h0tk3y.rally.strRound1
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * How early (negative) or late (positive) the crew is at [time], at the current distance of [raceModel], compared to the
 * time that [localizer] expects for that distance. Null if the localizer has no expected time for the distance.
 */
fun earlyLate(time: Instant, raceModel: RaceModel, localizer: TimeDistanceLocalizer): Duration? {
    val expectedTime = localizer.getExpectedTimeForDistance(raceModel.currentDistance) ?: return null
    return time - (raceModel.startAtTime + expectedTime.timeHours.hours)
}

/**
 * Formats an early/late value: seconds with a tenth below a minute (`-12.3`, `+0.4`), whole minutes and seconds above it
 * (`-01:05`, `+02:30`). Late values get a `+`, early values a `-`.
 */
fun formatEarlyLate(delta: Duration): String {
    val deltaSec = delta.inWholeMicroseconds.toDouble() / 1000_000
    return if (deltaSec.absoluteValue < 60.0) {
        val sign = if (delta <= Duration.ZERO) "" else "+"
        "$sign${deltaSec.strRound1()}"
    } else {
        val sign = if (delta < Duration.ZERO) "-" else if (delta == Duration.ZERO) "" else "+"
        "$sign${
            TimeMinSec(
                deltaSec.absoluteValue.roundToInt() / 60,
                deltaSec.absoluteValue.roundToInt() % 60,
                false
            )
        }"
    }
}
