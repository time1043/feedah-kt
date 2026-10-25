package com.time1043.feedah_kt.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayJumpTest {
    @Test
    fun publishThenConsumeReturnsDay() {
        DayJump.publishSelectedDay("2026-10-25")
        assertEquals("2026-10-25", DayJump.consumeSelectedDayChange())
    }

    @Test
    fun consumeIsChangeDetectionNotOneShotPerPublication() {
        DayJump.publishSelectedDay("2026-10-25")
        assertEquals("2026-10-25", DayJump.consumeSelectedDayChange())
        // Same day published again → list has already positioned for it.
        DayJump.publishSelectedDay("2026-10-25")
        assertNull(DayJump.consumeSelectedDayChange())
        // A different day re-anchors.
        DayJump.publishSelectedDay("2026-10-24")
        assertEquals("2026-10-24", DayJump.consumeSelectedDayChange())
    }

    @Test
    fun consumeWithoutPublishReturnsNull() {
        DayJump.publishSelectedDay("2026-10-25")
        DayJump.consumeSelectedDayChange()
        // drained
        assertNull(DayJump.consumeSelectedDayChange())
    }
}
