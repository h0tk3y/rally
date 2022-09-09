package com.h0tk3y.rally

data class LineNumber(val number: Int, val subNumber: Int) {
    init {
        require(number >= 1) { "line numbers should be positive" }
    }

    override fun toString(): String = "#$number"
}

sealed interface RoadmapInputLine {
    val lineNumber: LineNumber
}

data class CommentLine(
    val commentText: String,
    override val lineNumber: LineNumber
) : RoadmapInputLine {
    companion object {
        const val commentPrefix = "//"
    }
}

data class PositionLine(
    val atKm: DistanceKm,
    override val lineNumber: LineNumber,
    val modifiers: List<PositionLineModifier>
) : RoadmapInputLine

inline fun <reified T> PositionLine.modifier(): T? =
    modifiers.filterIsInstance<T>().let { matching ->
        when (matching.size) {
            0 -> null
            1 -> matching.single()
            else -> error("unexpected duplicate modifiers")
        }
    }

sealed interface PositionLineModifier {
    sealed interface SetAvg : PositionLineModifier {
        val setavg: SpeedKmh
    }
    
    sealed interface EndAvg : PositionLineModifier {
        val endavg: SpeedKmh?
    }
    
    data class SetAvgSpeed(
        override val setavg: SpeedKmh,
    ) : SetAvg

    data class ThenAvgSpeed(
        override val setavg: SpeedKmh
    ) : SetAvg, EndAvg {
        override val endavg get() = null
    }

    data class EndAvgSpeed(
        override val endavg: SpeedKmh?,
    ) : EndAvg
    
    data class Here(
        val atTime: TimeHr
    ) : PositionLineModifier
    
    data class AddSynthetic(
        val interval: DistanceKm,
        val count: Int
    ) : PositionLineModifier
    
    object IsSynthetic : PositionLineModifier
    
    object CalculateAverage : PositionLineModifier
    object EndCalculateAverage : PositionLineModifier
}

val PositionLine.isSetAvg: Boolean
    get() = modifier<PositionLineModifier.SetAvg>() != null

val PositionLine.isEndAvg: Boolean
    get() = modifier<PositionLineModifier.EndAvg>() != null

val PositionLine.isThenAvg: Boolean
    get() = modifier<PositionLineModifier.ThenAvgSpeed>() != null