package com.programminghut.realtime_object.helpers

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*
import kotlin.math.abs

class TTSHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var currentLanguage = "fr"
    private var currentCountry = "FR"

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setLanguage(currentLanguage, currentCountry)
            }
        }
    }

    fun setLanguage(lang: String, country: String) {
        currentLanguage = lang
        currentCountry = country
        val locale = Locale(lang, country)

        when (tts?.setLanguage(locale)) {
            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                tts?.language = Locale.FRENCH
            }

            else -> {
                tts?.language = locale
            }
        }
    }

    fun speak(text: String) {
        val processedText = if (currentLanguage == "ar") {
            convertNumbersToArabic(text)
        } else {
            convertNumbersToFrench(text)
        }

        tts?.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun convertNumbersToArabic(text: String): String {
        return text.replace(Regex("\\d+")) { matchResult ->
            val number = matchResult.value.toIntOrNull() ?: return@replace matchResult.value
            convertWesternToArabicNumerals(number.toString())
        }
    }

    private fun convertNumbersToFrench(text: String): String {
        return text.replace(Regex("\\d+\\.?\\d*")) { matchResult ->
            val number = matchResult.value.replace(",", ".")
            if (number.contains(".")) {
                val parts = number.split(".")
                "${convertWesternToFrenchNumerals(parts[0])},${parts.getOrNull(1) ?: "0"}"
            } else {
                convertWesternToFrenchNumerals(number)
            }
        }
    }

    private fun convertWesternToArabicNumerals(number: String): String {
        val arabicNumerals = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return number.map { c ->
            if (c.isDigit()) arabicNumerals[c - '0'] else c
        }.joinToString("")
    }

    private fun convertWesternToFrenchNumerals(number: String): String {
        return number
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}