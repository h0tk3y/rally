package com.h0tk3y.rally

import kotlinx.serialization.Serializable

@Serializable
data class LineNumber(val number: Int, val subNumber: Int) : Comparable<LineNumber> {
    init {
        require(number >= 1) { "line numbers should be positive" }
        require(subNumber >= 0) { "line numbers should be non-negative" }
    }

    override fun toString(): String = "#$number" + subNumber.let { if (it > 0) ":$it" else "" }

    override operator fun compareTo(other: LineNumber) =
        if (other.number != number)
            compareValues(number, other.number)
        else compareValues(subNumber, other.subNumber)
}

sealed interface RoadmapInputLine {
    val lineNumber: LineNumber
}

data class CommentLine(
    val commentText: String,
    override val lineNumber: LineNumber
) : RoadmapInputLine {
    companion object {
        const val COMMENT_PREFIX = "//"
    }
}

@Serializable
data class PositionLine(
    val atKm: DistanceKm,
    override val lineNumber: LineNumber,
    val modifiers: List<PositionLineModifier>
) : RoadmapInputLine {
    override fun toString(): String =
        atKm.valueKm.strRound3() + " " + ResultsFormatter().modifiersString(this)
}

inline fun <reified T> PositionLine.modifier(): T? =
    modifiers.filterIsInstance<T>().let { matching ->
        when (matching.size) {
            0 -> null
            1 -> matching.single()
            else -> error("unexpected duplicate modifiers")
        }
    }

@Serializable
sealed interface PositionLineModifier {
    @Serializable
    sealed interface SetAvg : PositionLineModifier {
        val setavg: SpeedKmh
    }

    @Serializable
    sealed interface EndAvg : PositionLineModifier {
        val endavg: SpeedKmh?
    }

    @Serializable
    data class SetAvgSpeed(
        override val setavg: SpeedKmh,
    ) : SetAvg

    @Serializable
    data class ThenAvgSpeed(
        override val setavg: SpeedKmh
    ) : SetAvg, EndAvg {
        override val endavg get() = null
    }
    
    @Serializable
    data class EndAvgSpeed(
        override val endavg: SpeedKmh?,
    ) : EndAvg

    @Serializable
    data class AstroTime(
        val timeOfDay: TimeDayHrMinSec
    ) : PositionLineModifier
    
    @Serializable
    data class OdoDistance(
        val distanceKm: DistanceKm
    ) : PositionLineModifier
    
    @Serializable
    data class Here(
        val atTime: TimeHr
    ) : PositionLineModifier

    @Serializable
    data class AddSynthetic(
        val interval: DistanceKm,
        val count: Int
    ) : PositionLineModifier

    @Serializable
    object IsSynthetic : PositionLineModifier

    @Serializable
    object CalculateAverage : PositionLineModifier
    
    @Serializable
    object EndCalculateAverage : PositionLineModifier
}

val PositionLine.isSetAvg: Boolean
    get() = modifier<PositionLineModifier.SetAvg>() != null

val PositionLine.isEndAvg: Boolean
    get() = modifier<PositionLineModifier.EndAvg>() != null

val PositionLine.isThenAvg: Boolean
    get() = modifier<PositionLineModifier.ThenAvgSpeed>() != null