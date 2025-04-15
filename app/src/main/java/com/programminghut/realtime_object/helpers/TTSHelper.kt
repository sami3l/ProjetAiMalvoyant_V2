package com.programminghut.realtime_object.helpers

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.*

@RequiresApi(Build.VERSION_CODES.DONUT)
class TTSHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var currentLocale: Locale = Locale("fr", "FR")

    init {
        tts = TextToSpeech(context) {
            if (it == TextToSpeech.SUCCESS) {
                Log.d("TTS", "TTS Ready ✅")
                isReady = true
                tts?.language = currentLocale
            } else {
                Log.e("TTS", "TTS Init failed ❌")
            }
        }
    }

    fun setLanguage(lang: String, country: String) {
        currentLocale = Locale(lang, country)
        val result = tts?.setLanguage(currentLocale)
        Log.d("TTS", "Langue définie : $lang-$country (result=$result)")
    }

    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w("TTS", "TTS not ready yet ❗️ Phrase ignorée: $text")
        }
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
