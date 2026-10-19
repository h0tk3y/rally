package com.h0tk3y.rally.android.scenes

import com.h0tk3y.rally.TimeDayHrMinSec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StageDataInputValidationTest {
    @Test
    fun `time without a separator is rejected rather than replaced with the default`() {
        assertNull(parseTimeInput("45"))
        assertTrue(isInvalidTimeInput("45"))
        assertFalse(isValidStageDataInput(distance = "", time = "45", speed = "", startTime = ""))
    }

    @Test
    fun `time with separators is parsed`() {
        assertEquals(TimeDayHrMinSec(0, 0, 45, 0), parseTimeInput("0:45"))
        assertEquals(TimeDayHrMinSec(0, 1, 2, 3), parseTimeInput("1:02:03"))
        assertEquals(TimeDayHrMinSec(0, 1, 30, 0), parseTimeInput("1.30"))
        assertFalse(isInvalidTimeInput("0:45"))
    }

    @Test
    fun `empty fields are valid because the placeholder values are used`() {
        assertTrue(isValidStageDataInput(distance = "", time = "", speed = "", startTime = ""))
    }

    @Test
    fun `invalid start time makes the input invalid`() {
        assertTrue(isInvalidStartTimeInput("930"))
        assertFalse(isValidStageDataInput(distance = "", time = "", speed = "", startTime = "930"))
        assertTrue(isValidStageDataInput(distance = "", time = "", speed = "", startTime = "9:30"))
    }

    @Test
    fun `invalid distance or speed makes the input invalid`() {
        assertFalse(isValidStageDataInput(distance = "1..2", time = "", speed = "", startTime = ""))
        assertFalse(isValidStageDataInput(distance = "", time = "", speed = "abc", startTime = ""))
        assertTrue(isValidStageDataInput(distance = "12.5", time = "0:15", speed = "50", startTime = ""))
    }

    @Test
    fun `zero and negative distance or speed are invalid`() {
        for (text in listOf("0", "0.0", "-0", "-5", "-0.1", "NaN", "Infinity")) {
            assertTrue(isInvalidDistanceInput(text), "distance $text")
            assertTrue(isInvalidSpeedInput(text), "speed $text")
        }
        assertFalse(isInvalidDistanceInput("0.1"))
        assertFalse(isInvalidSpeedInput("0.1"))
    }

    @Test
    fun `zero time is invalid and negative time cannot be entered`() {
        for (text in listOf("0:00", "00:00:00", "0.0", "0-0")) {
            assertTrue(isInvalidTimeInput(text), "time $text")
        }
        // '-' is a separator, so a leading minus leaves an empty part
        assertNull(parseTimeInput("-1:00"))
        assertNull(parseTimeInput("1:-30"))
        assertTrue(isInvalidTimeInput("-1:00"))
        assertFalse(isInvalidTimeInput("0:00:01"))
    }

    @Test
    fun `midnight is a valid start time`() {
        assertFalse(isInvalidStartTimeInput("00:00"))
        assertFalse(isInvalidStartTimeInput("0:00:00"))
        assertTrue(isInvalidStartTimeInput("-1:00"))
    }

    @Test
    fun `placeholder values are valid`() {
        assertFalse(isInvalidDistanceInput(defaultDistanceText))
        assertFalse(isInvalidTimeInput(defaultTimeText))
        assertFalse(isInvalidSpeedInput(defaultSpeedText))
    }

    @Test
    fun `dash-separated placeholder time is parsed as a positive one-hour time, not a negative value`() {
        assertEquals("01-00", defaultTimeText)
        assertEquals(TimeDayHrMinSec(0, 1, 0, 0), parseTimeInput(defaultTimeText))
        assertFalse(isInvalidTimeInput(defaultTimeText))
        assertTrue(isValidStageDataInput(distance = "", time = defaultTimeText, speed = "", startTime = "09-30"))
    }
}
