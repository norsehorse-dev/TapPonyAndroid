package com.tappony.android

import android.content.Context
import android.speech.tts.TextToSpeech
import com.tappony.core.Encoding
import java.util.Locale

/**
 * Spoken confirmation for hands-free rounds (PROFILE_SCHEMA.md section 15).
 * Uses the phone's own text-to-speech engine; nothing leaves the device.
 * Text said before the engine is ready waits and is spoken once it is.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val pending = ArrayList<String>()
    private var ready = false
    private var failed = false
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        synchronized(pending) {
            ready = status == TextToSpeech.SUCCESS
            failed = !ready
            if (ready) {
                tts.language = Locale.getDefault()
                pending.forEach { say(it) }
            }
            pending.clear()
        }
    }

    fun speak(text: String) {
        val t = Encoding.capCodePoints(text.trim(), MAX)
        if (t.isEmpty()) return
        synchronized(pending) {
            if (ready) say(t) else if (!failed) pending.add(t)
        }
    }

    private fun say(t: String) {
        tts.speak(t, TextToSpeech.QUEUE_ADD, null, "tappony-${System.nanoTime()}")
    }

    fun shutdown() = tts.shutdown()

    private companion object {
        const val MAX = 200
    }
}
