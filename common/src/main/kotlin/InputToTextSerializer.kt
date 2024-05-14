package com.h0tk3y.rally

import com.h0tk3y.rally.PositionLineModifier.*

object InputToTextSerializer {
    fun serializeToText(inputs: Collection<RoadmapInputLine>): String =
        inputs.joinToString("\n", transform = ::serializeLine)

    fun serializeLine(roadmapInputLine: RoadmapInputLine): String =
        when (roadmapInputLine) {
            is CommentLine -> {
                CommentLine.commentPrefix + roadmapInputLine.commentText
            }

            is PositionLine -> buildString {
                append(roadmapInputLine.atKm.valueKm.strRound3())
                if (roadmapInputLine.modifiers.isNotEmpty()) {
                    append(roadmapInputLine.modifiers.joinToString(prefix = " ", separator = " ") { modifier ->
                        when (modifier) {
                            is OdoDistance -> "odo ${modifier.distanceKm.valueKm.strRound3()}"
                            is SetAvgSpeed -> "setavg ${modifier.setavg.valueKmh.strRound3()}"
                            is EndAvgSpeed -> "endavg" +
                                if (modifier.endavg != null) {
                                    " " + modifier.endavg.valueKmh.strRound3()
                                } else ""
                            is AstroTime -> "atime ${modifier.timeOfDay.timeStr()}"

                            is ThenAvgSpeed -> "thenavg " + modifier.setavg.valueKmh.strRound3()
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