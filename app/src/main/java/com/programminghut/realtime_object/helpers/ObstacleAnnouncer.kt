package com.programminghut.realtime_object.helpers

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.*

class ObstacleAnnouncer(
    private val context: Context,
    private val tts: TTSHelper,
    private val lang: String
) {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val labelMap = mapOf(
        "person" to mapOf("fr" to "personne", "darija" to "shī wāḥed"),
        "car" to mapOf("fr" to "voiture", "darija" to "ṭonobil"),
        "truck" to mapOf("fr" to "camion", "darija" to "kamiyō"),
        "bicycle" to mapOf("fr" to "vélo", "darija" to "bīsīkla"),
        "motorcycle" to mapOf("fr" to "moto", "darija" to "moto"),
        "bus" to mapOf("fr" to "bus", "darija" to "ṭobīs"),
        "bench" to mapOf("fr" to "banc", "darija" to "korsi"),
        "traffic light" to mapOf("fr" to "feu", "darija" to "lampa dial traffic"),
        "stop sign" to mapOf("fr" to "panneau stop", "darija" to "stop"),
        "parking meter" to mapOf("fr" to "parcmètre", "darija" to "compteur parking"),
        "fire hydrant" to mapOf("fr" to "borne incendie", "darija" to "robiné dial lma")
    )

    private val lastSpoken = mutableMapOf<String, Long>()
    private val cooldownMs = 8000L

    fun announce(label: String) {
        val now = System.currentTimeMillis()
        if ((now - (lastSpoken[label] ?: 0L)) < cooldownMs) return

        val translated = labelMap[label]?.get(lang) ?: label
        val message = if (lang == "fr") "Attention, $translated devant" else "ḥder, kayn $translated"
        tts.speak(message)
        lastSpoken[label] = now

        // 📳 Vibration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }
}
