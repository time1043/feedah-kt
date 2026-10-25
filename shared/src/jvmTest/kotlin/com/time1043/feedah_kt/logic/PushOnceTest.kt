package com.time1043.feedah_kt.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class PushOnceTest {
    @kotlin.test.BeforeTest
    fun resetGate() {
        PushGate.reset()
    }

    @Test
    fun firstPushGoesThrough() {
        var calls = 0
        pushOnce { calls++ }
        assertEquals(1, calls)
    }

    @Test
    fun rapidSecondPushInsideWindowIsSwallowed() {
        var calls = 0
        pushOnce { calls++ }
        pushOnce { calls++ } // immediately after the first → inside the 800ms window
        assertEquals(1, calls)
    }
}
