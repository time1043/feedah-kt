package com.time1043.feedah_kt.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeatmapTest {
    @Test
    fun year2024IsALeapYearWith53WeekColumns() {
        // 2024-01-01 is a Monday (1 leading null), 2024 has 366 days:
        // 1 + 366 = 367 → 52 full weeks + 3 → 53 columns.
        val weeks = heatmapWeeks(2024)
        assertEquals(53, weeks.size)
        assertEquals(7, weeks[0].size)
        assertNull(weeks[0][0])
        assertEquals("2024-01-01", weeks[0][1])
        assertEquals("2024-12-31", weeks.last().last())
    }

    @Test
    fun yearStartingOnSundayHasNoLeadingNulls() {
        // 2023-01-01 was a Sunday; 365 days → 52 full weeks + a 1-day column.
        val weeks = heatmapWeeks(2023)
        assertEquals("2023-01-01", weeks[0][0])
        assertEquals(53, weeks.size)
        val last = weeks.last()
        assertEquals(1, last.size)
        assertEquals("2023-12-31", last[0])
    }

    @Test
    fun monthLabelsMarkMonthChanges() {
        val weeks = heatmapWeeks(2024)
        val labels = heatmapMonthLabels(weeks)
        assertEquals("Jan", labels[0])
        // A month is labeled on the first week column that STARTS in it:
        // week 4 begins Jan 28 (→ null), week 5 begins Feb 4 (→ Feb).
        assertEquals(null, labels[4])
        assertEquals("Feb", labels[5])
        assertEquals(null, labels[6])
        assertTrue(labels.filterNotNull().size == 12)
    }

    @Test
    fun levelLadderIsInclusive() {
        val thresholds = listOf(10L, 30L, 100L)
        assertEquals(0, heatmapLevel(null, thresholds))
        assertEquals(0, heatmapLevel(0, thresholds))
        assertEquals(1, heatmapLevel(10, thresholds))
        assertEquals(2, heatmapLevel(11, thresholds))
        assertEquals(3, heatmapLevel(100, thresholds))
        assertEquals(4, heatmapLevel(101, thresholds))
    }
}
