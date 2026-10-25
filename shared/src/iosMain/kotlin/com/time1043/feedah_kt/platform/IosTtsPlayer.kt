@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.time1043.feedah_kt.platform

import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate

class IosTtsPlayer : TtsPlayer {
    private val synthesizer = AVSpeechSynthesizer()

    override val isAvailable: Boolean get() = true

    override fun speak(text: String, rate: Float) {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance(string = text)
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate * rate
        synthesizer.speakUtterance(utterance)
    }

    override fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}
