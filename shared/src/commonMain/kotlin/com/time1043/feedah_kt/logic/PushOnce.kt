package com.time1043.feedah_kt.logic

import kotlin.time.Clock

/**
 * Swallows navigation presses that pile up while the app is busy. Opening a
 * data-heavy destination can hold the UI thread just long enough that a tap
 * looks unanswered — and every extra tap then dispatches its own push once
 * the thread frees, stacking duplicate pages. The first press inside the
 * window wins: queued taps all dispatch together after the freeze, so they
 * land inside one window and only the first goes through. A genuine second
 * navigation self-heals — the next tap after the window opens works.
 */
private const val WINDOW_MS = 800L

internal object PushGate {
    var lastAt = 0L
    fun reset() {
        lastAt = 0L
    }
}

fun pushOnce(push: () -> Unit) {
    val now = Clock.System.now().toEpochMilliseconds()
    if (now - PushGate.lastAt < WINDOW_MS) return
    PushGate.lastAt = now
    push()
}
