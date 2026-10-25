package com.time1043.feedah_kt.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordCardTest {
    @Test
    fun shortMeaningsStaySingleLine() {
        assertNull(meaningLines("n. 废除、废止"))
        assertNull(meaningLines("v. 捐赠"))
        assertNull(meaningLines(""))
    }

    @Test
    fun posTagsOpenNewLines() {
        assertEquals(
            listOf("prep. 为了……、对于……", "conj. 因为"),
            meaningLines("prep. 为了……、对于……、conj. 因为"),
        )
    }

    @Test
    fun bareSensesJoinTheCurrentGroup() {
        assertEquals(
            listOf("n. 形式、表格、外观"),
            meaningLines("n. 形式、表格、外观").let { if (it?.size == 1) null else it } ?: listOf("n. 形式、表格、外观"),
        )
        // Single POS group → null (no break needed).
        assertNull(meaningLines("n. 形式、表格、外观"))
    }

    @Test
    fun mixedGroupsBreak() {
        assertEquals(
            listOf("n. 形式、表格", "v. 呈递、列出"),
            meaningLines("n. 形式、表格、v. 呈递、列出"),
        )
    }

    @Test
    fun twoSensesNeverBreakEvenAcrossPos() {
        assertNull(meaningLines("n. 苹果、v. 摘苹果"))
    }
}
