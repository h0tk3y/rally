package com.h0tk3y.rally.android.racecervice

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class StreamedTelemetryPublicStateTest {
    @Test
    fun `waiting before the connection is established`() {
        assertEquals(
            TelemetryPublicState.WaitingForStream,
            streamedTelemetryPublicState(StreamConnectionState.NotInitialized, sinceLastFrame = null, hadFramesBefore = false)
        )
    }

    @Test
    fun `waiting when connected but no frame has arrived yet`() {
        assertEquals(
            TelemetryPublicState.WaitingForStream,
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = null, hadFramesBefore = false)
        )
    }

    @Test
    fun `receiving when the last frame is fresh`() {
        assertEquals(
            TelemetryPublicState.ReceivesStream(isDelayed = false),
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = 200.milliseconds, hadFramesBefore = true)
        )
    }

    @Test
    fun `delayed when the last frame is older than the threshold`() {
        assertEquals(
            TelemetryPublicState.ReceivesStream(isDelayed = true),
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = 1500.milliseconds, hadFramesBefore = true)
        )
        assertEquals(
            TelemetryPublicState.ReceivesStream(isDelayed = true),
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = streamDataDelayThreshold, hadFramesBefore = true)
        )
    }

    @Test
    fun `link lost when the connection drops after frames have arrived`() {
        assertEquals(
            TelemetryPublicState.StreamLinkLost,
            streamedTelemetryPublicState(StreamConnectionState.NotInitialized, sinceLastFrame = null, hadFramesBefore = true)
        )
    }

    @Test
    fun `link lost on a new connection until its first frame arrives`() {
        assertEquals(
            TelemetryPublicState.StreamLinkLost,
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = null, hadFramesBefore = true)
        )
        assertEquals(
            TelemetryPublicState.ReceivesStream(isDelayed = false),
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = 10.milliseconds, hadFramesBefore = true)
        )
    }

    @Test
    fun `incompatible regardless of frames`() {
        for (hadFramesBefore in listOf(false, true)) {
            assertEquals(
                TelemetryPublicState.IncompatibleStream,
                streamedTelemetryPublicState(StreamConnectionState.Incompatible, sinceLastFrame = null, hadFramesBefore)
            )
            assertEquals(
                TelemetryPublicState.IncompatibleStream,
                streamedTelemetryPublicState(StreamConnectionState.Incompatible, sinceLastFrame = 100.milliseconds, hadFramesBefore)
            )
        }
    }
}
