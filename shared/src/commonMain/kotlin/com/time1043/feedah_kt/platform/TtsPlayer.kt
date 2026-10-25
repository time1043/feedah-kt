package com.time1043.feedah_kt.platform

import com.time1043.feedah_kt.data.db.SpeechRate
import com.time1043.feedah_kt.data.db.SPEECH_RATE_VALUE

/**
 * Thin TTS abstraction over expo-speech's usage in the original (en-US words,
 * stop-before-speak). Each target supplies its own engine; desktop is a no-op.
 */
interface TtsPlayer {
    val isAvailable: Boolean

    /** [rate] is the multiplier from [SPEECH_RATE_VALUE] (1.0 = system default). */
    fun speak(text: String, rate: Float)

    fun stop()
}

/** Speaks a single English word, interrupting any queued speech first. */
fun speakWord(tts: TtsPlayer, text: String, rate: SpeechRate) {
    tts.stop()
    tts.speak(text, SPEECH_RATE_VALUE.getValue(rate))
}
