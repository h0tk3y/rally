package com.h0tk3y.rally

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoadmapParserTest {
    private fun parse(roadmap: String) =
        InputRoadmapParser(DefaultModifierValidator()).parseRoadmap(roadmap.reader()).filterIsInstance<PositionLine>()

    @Test
    fun `endavg without a speed followed by atime parses as two modifiers`() {
        val lines = parse(
            """
            0.0 setavg 60
            10.0 endavg atime 10:00:00
            """.trimIndent()
        )
        assertEquals(
            listOf(
                PositionLineModifier.EndAvgSpeed(null),
                PositionLineModifier.AstroTime(TimeDayHrMinSec(0, 10, 0, 0))
            ),
            lines[1].modifiers
        )
    }

    @Test
    fun `endavg with a speed still consumes it`() {
        val lines = parse(
            """
            0.0 setavg 60
            10.0 endavg 50
            """.trimIndent()
        )
        assertEquals(listOf(PositionLineModifier.EndAvgSpeed(SpeedKmh(50.0))), lines[1].modifiers)
    }

    @Test
    fun `setavg with an unparseable speed fails with a line error`() {
        assertFailsWith<IllegalStateException> { parse("0.0 setavg atime 10:00:00") }
    }
}
