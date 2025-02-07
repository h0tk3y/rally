package com.h0tk3y.rally

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeTest {
    @Test
    fun `test duration conversion to time hr-min-sec`() {
        val time = TimeHr(0.6 / 3600).toTimeDayHrMinSec()
        assertEquals(0, time.hr)
        assertEquals(0, time.min)
        assertEquals(1, time.sec)
    }
}