package com.time1043.feedah_kt.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class DailyTest {
    @Test
    fun sumsTimeStatsPerDay() {
        val usage = computeDailyUsage(
            stats = listOf(
                DailyStatRow("2026-10-24", 60, 120),
                DailyStatRow("2026-10-25", 30, 90),
                DailyStatRow("2026-10-24", 10, 5),
            ),
            pointers = emptyList(),
        )
        assertEquals(70, usage.getValue("2026-10-24").feedSeconds)
        assertEquals(125, usage.getValue("2026-10-24").appSeconds)
        assertEquals(30, usage.getValue("2026-10-25").feedSeconds)
    }

    @Test
    fun wordCountsAreHighWaterDifferences() {
        val usage = computeDailyUsage(
            stats = emptyList(),
            pointers = listOf(
                DailyPointerRow("2026-10-24", "2050", 10),
                DailyPointerRow("2026-10-25", "2050", 25),
                DailyPointerRow("2026-10-26", "2050", 20), // regression never inflates
            ),
        )
        assertEquals(10, usage.getValue("2026-10-24").words)
        assertEquals(15, usage.getValue("2026-10-25").words)
        assertEquals(0, usage.getValue("2026-10-26").words)
    }

    @Test
    fun bucketsAreIndependentAndInputOrderIrrelevant() {
        val usage = computeDailyUsage(
            stats = emptyList(),
            pointers = listOf(
                DailyPointerRow("2026-10-25", "700", 5),
                DailyPointerRow("2026-10-24", "2050", 8),
                DailyPointerRow("2026-10-24", "700", 2),
                DailyPointerRow("2026-10-25", "2050", 12),
            ),
        )
        assertEquals(8 + 12, usage.getValue("2026-10-24").words + usage.getValue("2026-10-25").words)
        assertEquals(2, usage.getValue("2026-10-24").words) // 2050 bucket only
        assertEquals(3, usage.getValue("2026-10-25").words) // 700 bucket only
    }

    @Test
    fun zeroAndNegativeDifferencesClampToZero() {
        val usage = computeDailyUsage(
            stats = emptyList(),
            pointers = listOf(
                DailyPointerRow("2026-10-24", "370", 0),
                DailyPointerRow("2026-10-25", "370", 0),
            ),
        )
        assertEquals(0, usage.getValue("2026-10-24").words)
        assertEquals(0, usage.getValue("2026-10-25").words)
    }
}
