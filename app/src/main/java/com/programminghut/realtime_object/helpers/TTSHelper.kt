package com.programminghut.realtime_object.helpers

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

class TTSHelper(context: Context) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) {
            if (it == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar", "MA")
            }
        }
    }

    fun setLanguage(lang: String, country: String) {
        tts?.language = Locale(lang, country)
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
