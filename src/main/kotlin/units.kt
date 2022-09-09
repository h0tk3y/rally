package com.h0tk3y.rally

import kotlin.math.roundToInt

data class SpeedKmh(val valueKmh: Double) {
    companion object {
        fun averageAt(distance: DistanceKm, timeHr: TimeHr): SpeedKmh =
            SpeedKmh(distance.valueKm / timeHr.timeHours)

        fun parse(fromString: String): SpeedKmh? {
            return when {
                fromString.endsWith(kmhSuffix) -> {
                    val valueKmh = fromString.substringBeforeLast(kmhSuffix).toDoubleOrNull()
                        ?: return null
                    SpeedKmh(valueKmh)
                }

                else -> null
            }
        }
        
        const val kmhSuffix = "kmh"
    }

    override fun toString(): String = "${(valueKmh * 100).roundToInt() / 100.0}$kmhSuffix"
}

data class DistanceKm(val valueKm: Double) {
    operator fun plus(other: DistanceKm) = DistanceKm(valueKm + other.valueKm)
    operator fun times(other: Double) = DistanceKm(valueKm * other)
    operator fun minus(other: DistanceKm) = DistanceKm(valueKm - other.valueKm)

    companion object {
        val zero = DistanceKm(0.0)
    }
}

data class TimeHr(val timeHours: Double) {
    fun toMinSec(): TimeMinSec {
        val sec = timeHours * 3600
        val min = (sec / 60).toInt()
        val remSec = (sec % 60).toInt()
        return TimeMinSec(min, remSec)
    }

    operator fun plus(other: TimeHr) = TimeHr(timeHours + other.timeHours)
    operator fun minus(other: TimeHr) = TimeHr(timeHours - other.timeHours)

    companion object {
        fun byMoving(distance: DistanceKm, atSpeedKmh: SpeedKmh) =
            TimeHr(distance.valueKm / atSpeedKmh.valueKmh)

        val zero = TimeHr(0.0)
    }

    override fun toString(): String = "TimeHr(hr = $timeHours, minSec = ${toMinSec()})"
}

data class TimeHrVector(val values: List<TimeHr>) {
    fun rebased(base: TimeHr) = TimeHrVector(values + (values.lastOrNull()?.plus(base) ?: base))
    val outer: TimeHr get() = values.last()
    companion object {
        fun of(timeHr: TimeHr) = TimeHrVector(listOf(timeHr))
    }
}

data class TimeMinSec(val min: Int, val sec: Int) {
    override fun toString(): String = "$min".padStart(2, '0') + ":" + "$sec".padStart(2, '0')
    fun toHr(): TimeHr = TimeHr((min * 60 + sec) / 3600.0)

    companion object {
        fun parse(fromString: String): TimeMinSec? {
            val minSec = fromString.split(":")
            val m = minSec[0].toIntOrNull()
            val s = minSec.getOrNull(1)?.toIntOrNull()
            if (m == null || s == null) {
                return null
            }
            return TimeMinSec(m, s)
        }
    }
}
