package com.time1043.feedah_kt.logic

/**
 * The stats heatmap's selected day, published for the words tab: the list
 * anchors at that day's first completed word — its bucket winning the tab —
 * or, for a day without records, back at the very first word. Tab screens
 * cannot receive params, so stats publishes and the words tab reads on its
 * next focus. Stats publishes wherever its selection changes — heatmap taps
 * and the focus reset to today alike — so the list always ends up mirroring
 * the heatmap.
 *
 * Consumption is change-detection, not one-shot: the list re-anchors only
 * when the day differs from the one it last positioned for, so flipping tabs
 * with the day unchanged never drags a mid-list browse. Memory only — a
 * stale day must not outlive the app.
 */
object DayJump {
    private var published: String? = null
    private var applied: String? = null

    fun publishSelectedDay(day: String) {
        published = day
    }

    /** Returns the newly selected day if the list hasn't positioned for it yet. */
    fun consumeSelectedDayChange(): String? {
        val value = published ?: return null
        if (value == applied) return null
        applied = value
        return value
    }
}
