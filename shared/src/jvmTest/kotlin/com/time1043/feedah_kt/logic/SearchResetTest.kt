package com.time1043.feedah_kt.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchResetTest {
    @Test
    fun requestFiresActiveListener() {
        var calls = 0
        val off = SearchReset.onSearchReset { calls++ }
        SearchReset.requestSearchReset()
        assertEquals(1, calls)
        off()
    }

    @Test
    fun disposeStopsDelivery() {
        var calls = 0
        val off = SearchReset.onSearchReset { calls++ }
        off()
        SearchReset.requestSearchReset()
        assertEquals(0, calls)
    }

    @Test
    fun newListenerReplacesTheSingleSlot() {
        var first = 0
        var second = 0
        val off1 = SearchReset.onSearchReset { first++ }
        SearchReset.onSearchReset { second++ }
        off1() // disposing the stale handle must not clear the new listener
        SearchReset.requestSearchReset()
        assertEquals(0, first)
        assertEquals(1, second)
    }
}
