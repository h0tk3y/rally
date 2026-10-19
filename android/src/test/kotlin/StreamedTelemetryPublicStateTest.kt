package com.h0tk3y.rally.android.racecervice

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StreamedTelemetryPublicStateTest {
    @Test
    fun `waiting before the connection is established`() {
        assertEquals(
            TelemetryPublicState.WaitingForStream,
            streamedTelemetryPublicState(StreamConnectionState.NotInitialized, sinceLastFrame = null)
        )
    }

    @Test
    fun `waiting when connected but no frame has arrived yet`() {
        assertEquals(
            TelemetryPublicState.WaitingForStream,
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = null)
        )
    }

    @Test
    fun `receiving when the last frame is fresh`() {
        assertEquals(
            TelemetryPublicState.ReceivesStream(isDelayed = false),
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = 200.milliseconds)
        )
    }

    @Test
    fun `delayed when the last frame is older than the threshold`() {
        assertEquals(
            TelemetryPublicState.ReceivesStream(isDelayed = true),
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = 1500.milliseconds)
        )
        assertEquals(
            TelemetryPublicState.ReceivesStream(isDelayed = true),
            streamedTelemetryPublicState(StreamConnectionState.Connected, sinceLastFrame = streamDataDelayThreshold)
        )
    }

    @Test
    fun `waiting again after the connection drops, even if frames were received before`() {
        assertEquals(
            TelemetryPublicState.WaitingForStream,
            streamedTelemetryPublicState(StreamConnectionState.NotInitialized, sinceLastFrame = 5.seconds)
        )
    }

    @Test
    fun `incompatible regardless of frames`() {
        assertEquals(
            TelemetryPublicState.IncompatibleStream,
            streamedTelemetryPublicState(StreamConnectionState.Incompatible, sinceLastFrame = null)
        )
        assertEquals(
            TelemetryPublicState.IncompatibleStream,
            streamedTelemetryPublicState(StreamConnectionState.Incompatible, sinceLastFrame = 100.milliseconds)
        )
    }
}
