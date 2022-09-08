package com.h0tk3y.rally

data class LineNumber(val number: Int) {
    init {
        require(number >= 1) { "line numbers should be positive" }
    }
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

sealed interface PositionLine : RoadmapInputLine {
    val atKm: DistanceKm

    data class SetAvgSpeed(
        override val atKm: DistanceKm,
        val avgspeed: SpeedKmh,
        override val lineNumber: LineNumber
    ) : PositionLine

    data class EndAvgSpeed(
        override val atKm: DistanceKm,
        val avgspeed: SpeedKmh,
        override val lineNumber: LineNumber
    ) : PositionLine

    data class Mark(
        override val atKm: DistanceKm,
        override val lineNumber: LineNumber
    ) : PositionLine
}
