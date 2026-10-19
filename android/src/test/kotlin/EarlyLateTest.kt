package com.h0tk3y.rally.model

import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.TimeDistanceLocalizer
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.android.scenes.eventDetailsText
import com.h0tk3y.rally.db.Event
import com.h0tk3y.rally.LineNumber
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class EarlyLateTest {
    private val start = Instant.fromEpochSeconds(1_000_000)

    /** Expects 1 minute per km from the race start. */
    private val oneMinutePerKm = object : TimeDistanceLocalizer {
        override val base = PositionLine(DistanceKm.zero, LineNumber(1, 0), emptyList())
        override fun getExpectedTimeForDistance(distanceKm: DistanceKm) = TimeHr(distanceKm.valueKm / 60)
    }

    private val noExpectedTime = object : TimeDistanceLocalizer {
        override val base = PositionLine(DistanceKm.zero, LineNumber(1, 0), emptyList())
        override fun getExpectedTimeForDistance(distanceKm: DistanceKm): TimeHr? = null
    }

    private fun modelAt(km: Double) =
        RaceModel(start, DistanceKm.zero, DistanceKm(km), DistanceKm.zero, distanceGoingUp = true, SpeedKmh(60.0))

    @Test
    fun `late is positive and early is negative`() {
        val late = earlyLate(start + 2.minutes + 3.seconds, modelAt(2.0), oneMinutePerKm)
        assertEquals(3.seconds, late!!.inWholeMilliseconds.milliseconds)
        val early = earlyLate(start + 1.minutes + 50.seconds, modelAt(2.0), oneMinutePerKm)
        assertEquals((-10).seconds, early!!.inWholeMilliseconds.milliseconds)
    }

    @Test
    fun `no early or late without an expected time`() {
        assertNull(earlyLate(start + 1.minutes, modelAt(1.0), noExpectedTime))
    }

    @Test
    fun `formats seconds with tenths below a minute and minutes with seconds above it`() {
        assertEquals("+1.2", formatEarlyLate(1200.milliseconds))
        assertEquals("-12.3", formatEarlyLate((-12300).milliseconds))
        assertEquals("0.0", formatEarlyLate(Duration.ZERO))
        assertEquals("+01:05", formatEarlyLate(65.seconds))
        assertEquals("-02:30", formatEarlyLate((-150).seconds))
    }

    private fun event(kind: RaceEventKind, since: Instant?, earlyLateMillis: Long?) =
        Event(1, 1, 1.0, kind, start + 5.minutes + 23.seconds, since, earlyLateMillis)

    @Test
    fun `event log shows early or late for a regularity finish`() {
        assertEquals(" (05:23 / +1.2)", eventDetailsText(event(RaceEventKind.RACE_FINISH, start, 1200)))
        assertEquals(" (05:23 / -01:05)", eventDetailsText(event(RaceEventKind.RACE_FINISH, start, -65_000)))
    }

    @Test
    fun `event log shows no early or late for a finish logged without it`() {
        assertEquals(" (05:23)", eventDetailsText(event(RaceEventKind.RACE_FINISH, start, null)))
        assertEquals("", eventDetailsText(event(RaceEventKind.RACE_UNDO_FINISH, null, null)))
    }
}
