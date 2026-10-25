package com.time1043.feedah_kt.platform

/** Desktop has no speech engine wired; pronunciation is a silent no-op there. */
object NoopTtsPlayer : TtsPlayer {
    override val isAvailable: Boolean get() = false
    override fun speak(text: String, rate: Float) {}
    override fun stop() {}
}
