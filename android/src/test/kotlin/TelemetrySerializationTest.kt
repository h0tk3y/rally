package com.h0tk3y.rally.android.racecervice

import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.PlainSpeedKmh
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.SpeedWithPercentage
import com.h0tk3y.rally.model.RaceState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class TelemetrySerializationTest {
    @Test
    fun `telemetry frame with speed polymorphism encodes and decodes properly`() {
        val speedWithPercentage = SpeedWithPercentage(PlainSpeedKmh(90.0), 50.0)
        val plainSpeed = PlainSpeedKmh(60.0)

        val frame = TelemetryFrame(
            section = null,
            serializedPositions = listOf(
                PositionLine(
                    lineNumber = LineNumber(1, 0),
                    atKm = DistanceKm(0.0),
                    modifiers = listOf(
                        PositionLineModifier.SetAvgSpeed(speedWithPercentage)
                    )
                ),
                PositionLine(
                    lineNumber = LineNumber(2, 0),
                    atKm = DistanceKm(10.0),
                    modifiers = listOf(
                        PositionLineModifier.ThenAvgSpeed(plainSpeed),
                        PositionLineModifier.EndAvgSpeed(plainSpeed)
                    )
                )
            ),
            instant = kotlin.time.Clock.System.now(),
            currentLine = LineNumber(1, 0),
            currentRaceLine = LineNumber(1, 0),
            rememberSpeed = speedWithPercentage,
            raceState = RaceState.NotStarted,
            speedLimitPercent = "50.0"
        )

        val encoded = SerializationUtils.json.encodeToString(frame)
        val decoded = SerializationUtils.json.decodeFromString<TelemetryFrame>(encoded)

        assertEquals(frame.currentLine, decoded.currentLine)
        assertEquals(frame.currentRaceLine, decoded.currentRaceLine)
        assertEquals(frame.speedLimitPercent, decoded.speedLimitPercent)

        val decodedRememberSpeed = assertIs<SpeedWithPercentage>(decoded.rememberSpeed)
        assertEquals(90.0, decodedRememberSpeed.baseSpeedKmh)
        assertEquals(50.0, decodedRememberSpeed.percentage)
        assertEquals(45.0, decodedRememberSpeed.valueKmh)

        val decodedPos1Modifier = assertIs<PositionLineModifier.SetAvgSpeed>(decoded.serializedPositions?.get(0)?.modifiers?.get(0))
        val decodedSetAvg = assertIs<SpeedWithPercentage>(decodedPos1Modifier.setavg)
        assertEquals(90.0, decodedSetAvg.baseSpeedKmh)
        assertEquals(50.0, decodedSetAvg.percentage)

        val decodedPos2Modifier = assertIs<PositionLineModifier.ThenAvgSpeed>(decoded.serializedPositions?.get(1)?.modifiers?.get(0))
        val decodedThenAvg = assertIs<PlainSpeedKmh>(decodedPos2Modifier.setavg)
        assertEquals(60.0, decodedThenAvg.valueKmh)
    }

    @Test
    fun `race state keeps the early or late value of the finish`() {
        val model = com.h0tk3y.rally.model.RaceModel(
            kotlin.time.Instant.fromEpochSeconds(1000), DistanceKm(1.0), DistanceKm(2.5), DistanceKm.zero, true, PlainSpeedKmh(60.0)
        )
        val state: RaceState = RaceState.Going(1L, model, model, kotlin.time.Instant.fromEpochSeconds(1090), (-12300).milliseconds)

        val decoded = SerializationUtils.json.decodeFromString<RaceState>(SerializationUtils.json.encodeToString(state))

        assertEquals(state, decoded)
        assertEquals((-12300).milliseconds, assertIs<RaceState.Going>(decoded).finishedEarlyLate)
    }
}
