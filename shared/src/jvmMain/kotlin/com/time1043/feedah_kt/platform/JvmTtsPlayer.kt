package com.time1043.feedah_kt.platform

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.math.roundToInt

/**
 * Desktop speech via the OS engines, standing in for expo-speech:
 *  - Windows: one persistent PowerShell System.Speech process fed over stdin
 *    ("text|rate" lines). Every line cancels pending speech first, which is
 *    exactly the stop-then-speak contract of [speakWord] — and the engine
 *    stays warm, so repeated words are instant.
 *  - macOS: `say -r wpm`; Linux: `spd-say` with an `espeak` fallback.
 */
class JvmTtsPlayer : TtsPlayer {
    private val os = System.getProperty("os.name").lowercase()
    private val isWindows = "windows" in os
    private val isMac = "mac" in os || "darwin" in os

    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var process: Process? = null
    @Volatile private var currentOneShot: Process? = null

    @Volatile override var isAvailable: Boolean = false
        private set

    init {
        if (isWindows) startEngine()
        else isAvailable = true // per-utterance processes; failures no-op per speak
    }

    private fun startEngine() {
        try {
            val builder = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "Add-Type -AssemblyName System.Speech; " +
                    "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    "while (\$true) { " +
                    "\$line = [Console]::In.ReadLine(); " +
                    "if (\$null -eq \$line) { break }; " +
                    "\$parts = \$line.Split('|'); " +
                    "if (\$parts[0] -eq '__stop__') { \$s.SpeakAsyncCancelAll(); continue }; " +
                    "\$s.SpeakAsyncCancelAll(); " +
                    "\$s.Rate = [int]\$parts[1]; " +
                    "\$s.SpeakAsync(\$parts[0]) | Out-Null }",
            )
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            builder.redirectError(ProcessBuilder.Redirect.DISCARD)
            val p = builder.start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
            isAvailable = true
        } catch (_: Exception) {
            isAvailable = false
        }
    }

    override fun speak(text: String, rate: Float) {
        if (!isAvailable) return
        val spoken = text.replace('\n', ' ').replace('|', ' ').trim()
        if (spoken.isEmpty()) return
        if (isWindows) {
            val sapiRate = ((rate - 1f) * 10f).roundToInt().coerceIn(-5, 5)
            writeLine("$spoken|$sapiRate")
        } else if (isMac) {
            spawn("say", listOf("-r", "${(175 * rate).roundToInt()}", spoken))
        } else {
            val wpm = "${(175 * rate).roundToInt()}"
            if (!spawn("spd-say", listOf("-r", wpm, spoken))) {
                spawn("espeak", listOf("-s", wpm, spoken))
            }
        }
    }

    override fun stop() {
        if (isWindows) {
            writeLine("__stop__|0")
        } else {
            currentOneShot?.destroyForcibly()
            currentOneShot = null
        }
    }

    private fun writeLine(line: String) {
        synchronized(this) {
            val w = writer ?: return
            try {
                w.write(line)
                w.newLine()
                w.flush()
            } catch (_: Exception) {
                // Engine died (console closed, machine slept) — restart once so
                // the next utterance works instead of silencing the session.
                isAvailable = false
                process?.destroyForcibly()
                process = null
                writer = null
                startEngine()
            }
        }
    }

    private fun spawn(command: String, args: List<String>): Boolean = try {
        val p = ProcessBuilder(listOf(command) + args).start()
        currentOneShot = p
        true
    } catch (_: Exception) {
        false
    }
}
