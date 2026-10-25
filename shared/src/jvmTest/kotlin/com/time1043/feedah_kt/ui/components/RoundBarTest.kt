package com.time1043.feedah_kt.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundBarTest {
    @Test
    fun emptyOrZeroWidthYieldsNoColumns() {
        assertTrue(aggregateRoundColumns(emptyList(), 100).isEmpty())
        assertTrue(aggregateRoundColumns(listOf(RoundWordStatus.green), 0).isEmpty())
    }

    @Test
    fun onePixelPerWordWhenBarIsWideEnough() {
        val statuses = List(5) { RoundWordStatus.gray } + RoundWordStatus.green
        val columns = aggregateRoundColumns(statuses, 10)
        assertEquals(statuses, columns)
    }

    @Test
    fun redWinsOverGreenInsideOnePixel() {
        val statuses = listOf(RoundWordStatus.green, RoundWordStatus.red, RoundWordStatus.green)
        val columns = aggregateRoundColumns(statuses, 1)
        assertEquals(listOf(RoundWordStatus.red), columns)
    }

    @Test
    fun lastColumnAbsorbsTheRemainder() {
        val statuses = List(10) { RoundWordStatus.gray }
        // 3 pixels → groups of 3.33; last column covers 7..10 (4 words).
        val columns = aggregateRoundColumns(statuses, 3)
        assertEquals(3, columns.size)
        assertEquals(List(3) { RoundWordStatus.gray }, columns)
    }

    @Test
    fun widerThanWordCountIsOnePixelEach() {
        val statuses = listOf(RoundWordStatus.red, RoundWordStatus.green, RoundWordStatus.gray)
        val columns = aggregateRoundColumns(statuses, 500)
        assertEquals(statuses, columns)
    }
}
