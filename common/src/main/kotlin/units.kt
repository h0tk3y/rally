package com.h0tk3y.rally

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

data class DistanceKm(val valueKm: Double) {
    operator fun plus(other: DistanceKm) = DistanceKm(valueKm + other.valueKm)
    operator fun times(other: Double) = DistanceKm(valueKm * other)
    operator fun minus(other: DistanceKm) = DistanceKm(valueKm.minusWithInf(other.valueKm))

    companion object {
        val zero = DistanceKm(0.0)
    }
}

data class TimeHr(val timeHours: Double): Comparable<TimeHr> {
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

data class TimeOfDay(val dayOverflow: Int = 0, val hr: Int, val min: Int, val sec: Int) {
    init {
        require(hr in 0..23)
        require(min in 0..59)
        require(sec in 0..59)
        require(dayOverflow >= 0)
    }

    operator fun plus(timeMinSec: TimeMinSec): TimeOfDay {
        val newSecRaw = sec + timeMinSec.sec
        val newSec = newSecRaw % 60
        val secOverflowMin = newSecRaw / 60

        val newMinRaw = min + timeMinSec.min + secOverflowMin
        val newMin = newMinRaw % 60
        val minOverflowHr = newMinRaw / 60

        val newHrRaw = hr + minOverflowHr
        val newHr = newHrRaw % 24
        val hrOverflowDay = newHrRaw / 24

        val newDayOverflow = dayOverflow + hrOverflowDay

        return TimeOfDay(newDayOverflow, newHr, newMin, newSec)
    }

    operator fun plus(other: TimeOfDay): TimeOfDay {
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

        return TimeOfDay(newDayOverflow, newHr, newMin, newSec)
    }

    fun timeStrNoDayOverflow(): String =
        "${hr.toString().padStart(2, '0')}:" +
                "${min.toString().padStart(2, '0')}:" +
                "${sec.toString().padStart(2, '0')}"

    fun timeStr(): String = "$dayOverflow:" + timeStrNoDayOverflow()

    companion object {
        fun parse(string: String): TimeOfDay {
            val parts = string.split(":")
            if (parts.size == 3) {
                return TimeOfDay(0, parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            } else if (parts.size == 4) {
                return TimeOfDay(
                    parts[0].toInt(),
                    parts[1].toInt(),
                    parts[2].toInt(),
                    parts[3].toInt()
                )
            } else {
                throw IllegalArgumentException("expected time of day, got $string")
            }
        }
    }
}

interface RelativeSubTimesTree {
    fun put(position: PositionLine, relativeTo: PositionLine, time: TimeHr)
    fun get(position: PositionLine, relativeTo: PositionLine): TimeHr
    fun relativesChain(position: PositionLine): List<PositionLine>
    fun globalTime(position: PositionLine): TimeHr
}

class RelativeSubTimesTreeImpl(globalStart: PositionLine) : RelativeSubTimesTree {
    private class Node(val position: PositionLine, var parent: PositionLine?) {
        val children: MutableMap<Node, TimeHr> = mutableMapOf()
    }
    
    private val nodeByPosition: MutableMap<PositionLine, Node> = mutableMapOf()
    
    private fun addNode(node: Node, withRelativeTime: TimeHr) {
        nodeByPosition[node.position] = node
        node.parent?.let { parent ->
            nodeByPosition.getValue(parent).children.put(node, withRelativeTime)
        }
    }
    
    private val root = Node(globalStart, parent = null).apply { addNode(this, TimeHr.zero) }
    
    override fun put(position: PositionLine, relativeTo: PositionLine, time: TimeHr) {
        TODO()
    }

    override fun get(position: PositionLine, relativeTo: PositionLine): TimeHr {
        TODO("Not yet implemented")
    }

    override fun relativesChain(position: PositionLine): List<PositionLine> {
        TODO("Not yet implemented")
    }

    override fun globalTime(position: PositionLine): TimeHr {
        TODO("Not yet implemented")
    }
}

fun Double.minusWithInf(other: Double) = if (this.isInfinite()) this else this - other