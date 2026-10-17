package com.h0tk3y.rally

import com.h0tk3y.rally.PositionLineModifier.*

object InputToTextSerializer {
    fun serializeToText(inputs: Collection<RoadmapInputLine>): String =
        inputs.joinToString("\n", transform = ::serializeLine)

    fun serializeLine(roadmapInputLine: RoadmapInputLine): String =
        when (roadmapInputLine) {
            is CommentLine -> {
                CommentLine.COMMENT_PREFIX + roadmapInputLine.commentText
            }

            is PositionLine -> buildString {
                append(roadmapInputLine.atKm.valueKm.strRound3())
                if (roadmapInputLine.modifiers.isNotEmpty()) {
                    append(roadmapInputLine.modifiers.joinToString(prefix = " ", separator = " ") { modifier ->
                        when (modifier) {
                            is OdoDistance -> "odo ${modifier.distanceKm.valueKm.strRound3()}"
                            is SetAvgSpeed -> "setavg ${formatSpeed(modifier.setavg)}"
                            is EndAvgSpeed -> "endavg" +
                                if (modifier.endavg != null) {
                                    " " + formatSpeed(modifier.endavg)
                                } else ""
                            is AstroTime -> "atime ${modifier.timeOfDay.timeStr()}"

                            is ThenAvgSpeed -> "thenavg " + formatSpeed(modifier.setavg)
                            is Here -> "here " + modifier.atTime.toMinSec()
                            is AddSynthetic -> "s ${modifier.interval.valueKm.strRound3()} ${modifier.count}"
                            is IsSynthetic -> error("synthetic positions should not appear in the inputs")
                            is CalculateAverage -> "calc"
                            is EndCalculateAverage -> "endcalc"
                        }
                    })
                }
            }
        }
}

private fun formatSpeed(speed: SpeedKmh): String = when (speed) {
    is SpeedWithPercentage -> "${speed.baseSpeed.valueKmh.strRound3()};${speed.percentage.formatPercentage()}%"
    is PlainSpeedKmh -> speed.valueKmh.strRound3()
}