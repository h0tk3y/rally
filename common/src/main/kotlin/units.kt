package com.h0tk3y.rally

import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

data class SpeedKmh(val valueKmh: Double) {
    companion object {
        fun averageAt(distance: DistanceKm, timeHr: TimeHr): SpeedKmh =
            SpeedKmh(distance.valueKm / timeHr.timeHours)

        fun parse(fromString: String): SpeedKmh? {
            return when {
                fromString.endsWith(KMH_SUFFIX) -> {
                    val valueKmh = fromString.substringBeforeLast(KMH_SUFFIX).toDoubleOrNull()
                        ?: return null
                    SpeedKmh(valueKmh)
                }

                else -> null
            }
        }

        const val KMH_SUFFIX = "kmh"
    }

    override fun toString(): String = if (valueKmh.isInfinite()) "∞" else "${(valueKmh * 100).roundToInt() / 100.0}$KMH_SUFFIX"
}

data class DistanceKm(val valueKm: Double) : Comparable<DistanceKm> {
    operator fun plus(other: DistanceKm) = DistanceKm(valueKm + other.valueKm)
    operator fun times(other: Double) = DistanceKm(valueKm * other)
    operator fun minus(other: DistanceKm) = DistanceKm(valueKm.minusWithInf(other.valueKm))

    companion object {
        fun byMoving(speedKmh: SpeedKmh, timeHr: TimeHr): DistanceKm {
            return DistanceKm(speedKmh.valueKmh * timeHr.timeHours)
        }

        val zero = DistanceKm(0.0)
    }

    override fun compareTo(other: DistanceKm): Int = compareValues(valueKm, other.valueKm)
}

data class TimeHr(val timeHours: Double) : Comparable<TimeHr> {
    init {
        if (timeHours.isNaN())
            throw IllegalStateException()
    }

    fun toMinSec(): TimeMinSec {
        if (timeHours.isNaN()) {
            return TimeMinSec(-1, -1, false)
        }
        val sec = (timeHours * 3600).roundToInt()
        val min = (sec / 60)
        val remSec = (sec % 60)
        return TimeMinSec(min, remSec, timeHours.isInfinite())
    }

    fun toTimeDayHrMinSec() = TimeDayHrMinSec(0, 0, 0, 0) + toMinSec()

    operator fun plus(other: TimeHr) = TimeHr(timeHours + other.timeHours)
    operator fun minus(other: TimeHr) = TimeHr(timeHours.minusWithInf(other.timeHours))

    companion object {
        fun byMoving(distance: DistanceKm, atSpeedKmh: SpeedKmh) =
            TimeHr(distance.valueKm / atSpeedKmh.valueKmh)

        val zero = TimeHr(0.0)
    }

    override fun compareTo(other: TimeHr): Int = compareValues(timeHours, other.timeHours)

    override fun toString(): String = "TimeHr(hr = $timeHours, minSec = ${toMinSec()})"
}

data class TimeHrVector(val values: List<TimeHr>) {
    fun rebased(base: TimeHr) = TimeHrVector(values + (values.lastOrNull()?.plus(base) ?: base))
    fun mapOuter(mapFn: (outer: TimeHr) -> TimeHr) =
        TimeHrVector(values.dropLast(1) + if (values.isNotEmpty()) listOf(values.last().let(mapFn)) else emptyList())

    val outer: TimeHr get() = values.last()

    companion object {
        fun of(timeHr: TimeHr) = TimeHrVector(listOf(timeHr))
    }
}

data class TimeMinSec(val min: Int, val sec: Int, val isInfinity: Boolean) {
    override fun toString(): String = if (isInfinity) "∞" else "$min".padStart(2, '0') + ":" + "$sec".padStart(2, '0')
    fun toHr(): TimeHr = TimeHr((min * 60 + sec) / 3600.0)

    companion object {
        fun parse(fromString: String): TimeMinSec? {
            val minSec = fromString.split(":")
            val m = minSec[0].toIntOrNull()
            val s = minSec.getOrNull(1)?.toIntOrNull()
            if (m == null || s == null) {
                return null
            }
            return TimeMinSec(m, s, false)
        }
    }
}

data class TimeDayHrMinSec(val dayOverflow: Int = 0, val hr: Int, val min: Int, val sec: Int) {
    init {
        require(hr in 0..23)
        require(min in 0..59)
        require(sec in 0..59)
    }

    val timeSinceMidnight: TimeHr get() = TimeHr(dayOverflow * 24 + hr + min / 60.0 + sec / 3600.0)

    val secondsOfDay: Int get() = sec + min * 60 + hr * 3600 + dayOverflow * (24 * 3600)

    operator fun plus(timeMinSec: TimeMinSec): TimeDayHrMinSec {
        fun divModNonNegative(dividend: Int, divisor: Int): Pair<Int, Int> {
            val quotient = dividend / divisor
            val remainder = dividend % divisor
            return when {
                remainder < 0 -> quotient - 1 to remainder + divisor
                else -> quotient to remainder
            }
        }

        val newSecRaw = sec + timeMinSec.sec
        val (secOverflowMin, newSec) = divModNonNegative(newSecRaw, 60)

        val newMinRaw = min + timeMinSec.min + secOverflowMin
        val (minOverflowHr, newMin) = divModNonNegative(newMinRaw, 60)

        val newHrRaw = hr + minOverflowHr
        val (hrOverflowDay, newHr) = divModNonNegative(newHrRaw, 24)

        val newDayOverflow = dayOverflow + hrOverflowDay

        return TimeDayHrMinSec(newDayOverflow, newHr, newMin, newSec)
    }

    operator fun plus(other: TimeDayHrMinSec): TimeDayHrMinSec {
        val newSecRaw = sec + other.sec
        val newSec = newSecRaw % 60
        val secOverflowMin = newSecRaw / 60

        val newMinRaw = min + other.min + secOverflowMin
        val newMin = newMinRaw % 60
        val minOverflowHr = newMinRaw / 60

        val newHrRaw = hr + other.hr + minOverflowHr
        val newHr = newHrRaw / 24
        val hrOverflowDay = newHrRaw % 24

        val newDayOverflow = dayOverflow + other.dayOverflow + hrOverflowDay

        return TimeDayHrMinSec(newDayOverflow, newHr, newMin, newSec)
    }

    fun timeStrNoDayOverflow(): String =
        "${hr.toString().padStart(2, '0')}:" +
                "${min.toString().padStart(2, '0')}:" +
                sec.toString().padStart(2, '0')

    fun timeStrNoHoursIfZero(): String =
        (if (hr == 0) "" else "${hr.toString().padStart(2, '0')}:") +
                "${min.toString().padStart(2, '0')}:" +
                sec.toString().padStart(2, '0')

    fun timeStr(): String = "$dayOverflow:" + timeStrNoDayOverflow()

    companion object {
        fun tryParse(string: String): TimeDayHrMinSec? {
            val parts = string.split(":")
            if (parts.size == 3) {
                return TimeDayHrMinSec(0, 
                    parts[0].toInt().takeIf { it in 0..23 } ?: return null, 
                    parts[1].toInt().takeIf { it in 0..59 } ?: return null,  
                    parts[2].toInt().takeIf { it in 0..59 } ?: return null
                )
            } else if (parts.size == 4) {
                return TimeDayHrMinSec(
                    parts[0].toInt(),
                    parts[1].toInt().takeIf { it in 0..23 } ?: return null,
                    parts[2].toInt().takeIf { it in 0..59 } ?: return null,
                    parts[3].toInt().takeIf { it in 0..59 } ?: return null
                )
            } else {
                throw IllegalArgumentException("expected time of day, got $string")
            }
        }
        
        fun of(time: LocalTime): TimeDayHrMinSec = TimeDayHrMinSec(0, time.hour, time.minute, time.second)
    }
}

fun Double.minusWithInf(other: Double) = if (this.isInfinite()) this else this - other

fun DistanceKm.roundTo3Digits(): DistanceKm = 
    valueKm.strRound3().toDouble().let(::DistanceKm)