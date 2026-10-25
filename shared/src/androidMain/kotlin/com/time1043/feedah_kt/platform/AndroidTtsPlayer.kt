package com.time1043.feedah_kt.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class AndroidTtsPlayer(context: Context) : TtsPlayer {
    private var engine: TextToSpeech? = null

    @Volatile private var ready = false

    init {
        engine = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }

    override val isAvailable: Boolean get() = ready

    override fun speak(text: String, rate: Float) {
        val tts = engine ?: return
        if (!ready) return
        tts.stop()
        tts.language = Locale.US
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "feedah-word")
    }

    override fun stop() {
        engine?.stop()
    }
}
